package com.immunization.validator.service;

import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.ValidationResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * DateConditionEvaluator for Massachusetts immunization requirements.
 * Validates that vaccine doses meet date-based conditions.
 *
 * Version 3.0 - Tri-state validation with proper UNDETERMINED handling
 *
 * Supported validations:
 * - Birthday-based: "Nth dose on or after Yth birthday"
 *   Examples: "1st dose on or after 1st birthday", "4th dose on or after 4th birthday"
 *
 * - Month-based: "Nth dose on or after Yth month"
 *   Examples: "1st dose on or after 15th month", "2nd dose on or after 6th month"
 *
 * Implementation Notes:
 * - This evaluator DOES NOT assume pre-sorted immunizations
 * - Sorts immunizations internally by occurrenceDateTime for safety
 * - Returns tri-state results: SATISFIED, NOT_SATISFIED, or UNDETERMINED
 *
 * @author David Saaka
 * @version 3.0 - Proper tri-state implementation with UNDETERMINED and defensive sorting
 */
@Slf4j
@Component
public class DateConditionEvaluator {

    // Pattern to match: "Nth dose on or after Yth birthday"
    // Examples: "4th dose on or after 4th birthday", "1st dose on or after 1st birthday"
    private static final Pattern BIRTHDAY_PATTERN = Pattern.compile(
            "(\\d+)(?:st|nd|rd|th)\\s+dose\\s+on or after\\s+(\\d+)(?:st|nd|rd|th)\\s+birthday",
            Pattern.CASE_INSENSITIVE
    );

    // Pattern to match: "Nth dose on or after Yth month"
    // Examples: "1st dose on or after 15th month", "2nd dose on or after 6th month"
    private static final Pattern MONTH_PATTERN = Pattern.compile(
            "(\\d+)(?:st|nd|rd|th)\\s+dose\\s+on or after\\s+(\\d+)(?:st|nd|rd|th)\\s+month",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Evaluates multiple date conditions (all must be met).
     *
     * This method is called when a requirement has multiple date conditions.
     * All conditions must be satisfied for the requirement to be met.
     *
     * Combination Rules (AND logic):
     * - If ANY condition is NOT_SATISFIED → NOT_SATISFIED
     * - If ANY condition is UNDETERMINED (and none are NOT_SATISFIED) → UNDETERMINED
     * - If ALL conditions are SATISFIED → SATISFIED
     *
     * @param conditions List of date conditions to evaluate
     * @param immunizations List of immunizations for the vaccine (will be sorted internally)
     * @param birthDate Patient's birth date
     * @return Combined ValidationResult using AND logic
     */
    public ValidationResult evaluateConditions(List<String> conditions,
                                               List<Immunization> immunizations,
                                               LocalDate birthDate) {
        if (conditions == null || conditions.isEmpty()) {
            log.debug("No date conditions to evaluate");
            return ValidationResult.SATISFIED;
        }

        log.debug("Evaluating {} date conditions", conditions.size());

        // Defensive sort - ensure immunizations are in chronological order
        List<Immunization> sortedImmunizations = sortImmunizationsByDate(immunizations);

        List<ValidationResult> results = new ArrayList<>();

        for (String condition : conditions) {
            ValidationResult result = evaluateCondition(condition, sortedImmunizations, birthDate);
            results.add(result);

            if (result == ValidationResult.NOT_SATISFIED) {
                log.info("❌ Date condition NOT satisfied: '{}'", condition);
            } else if (result == ValidationResult.UNDETERMINED) {
                log.warn("⚠️ Date condition UNDETERMINED: '{}'", condition);
            } else {
                log.debug("✅ Date condition satisfied: '{}'", condition);
            }
        }

        // Combine all results using AND logic
        ValidationResult combined = ValidationResult.and(results.toArray(new ValidationResult[0]));

        log.debug("Combined result of {} conditions: {}", conditions.size(), combined);
        return combined;
    }

    /**
     * Evaluates a single date condition.
     *
     * Returns:
     * - SATISFIED: Condition is definitely met
     * - NOT_SATISFIED: Condition is definitely not met
     * - UNDETERMINED: Cannot evaluate condition (missing data, parse error, etc.)
     *
     * Supports two types of conditions:
     * 1. Birthday-based: "4th dose on or after 4th birthday"
     * 2. Month-based: "1st dose on or after 15th month"
     *
     * @param condition Date condition to evaluate
     * @param immunizations List of immunizations for the vaccine (will be sorted internally)
     * @param birthDate Patient's birth date
     * @return ValidationResult (SATISFIED, NOT_SATISFIED, or UNDETERMINED)
     */
    public ValidationResult evaluateCondition(String condition,
                                              List<Immunization> immunizations,
                                              LocalDate birthDate) {
        // Empty condition
        if (condition == null || condition.trim().isEmpty()) {
            log.warn("Empty date condition provided");
            return ValidationResult.UNDETERMINED;
        }

        // Missing birth date
        if (birthDate == null) {
            log.warn("Cannot evaluate date condition '{}' - birth date is null", condition);
            return ValidationResult.UNDETERMINED;
        }

        log.debug("Evaluating date condition: '{}'", condition);

        // Defensive sort - ensure immunizations are in chronological order
        List<Immunization> sortedImmunizations = sortImmunizationsByDate(immunizations);

        // Try birthday pattern first
        Matcher birthdayMatcher = BIRTHDAY_PATTERN.matcher(condition);
        if (birthdayMatcher.find()) {
            return evaluateBirthdayCondition(birthdayMatcher, condition, sortedImmunizations, birthDate);
        }

        // Try month pattern
        Matcher monthMatcher = MONTH_PATTERN.matcher(condition);
        if (monthMatcher.find()) {
            return evaluateMonthCondition(monthMatcher, condition, sortedImmunizations, birthDate);
        }

        // Neither pattern matched - UNDETERMINED
        log.warn("Could not parse date condition: '{}'. Supported patterns: " +
                "'Nth dose on or after Yth birthday' or 'Nth dose on or after Yth month'", condition);
        return ValidationResult.UNDETERMINED;
    }

    /**
     * Evaluates a birthday-based condition.
     *
     * @param matcher Regex matcher containing dose number and birthday year
     * @param condition Original condition string (for logging)
     * @param immunizations List of immunizations (already sorted)
     * @param birthDate Patient's birth date
     * @return ValidationResult (SATISFIED, NOT_SATISFIED, or UNDETERMINED)
     */
    private ValidationResult evaluateBirthdayCondition(Matcher matcher,
                                                       String condition,
                                                       List<Immunization> immunizations,
                                                       LocalDate birthDate) {
        try {
            int doseNumber = Integer.parseInt(matcher.group(1));
            int birthdayYear = Integer.parseInt(matcher.group(2));

            log.debug("Parsed BIRTHDAY condition - Dose: {}, Birthday year: {}", doseNumber, birthdayYear);

            // Check if we have enough doses
            if (immunizations == null || immunizations.size() < doseNumber) {
                log.debug("Not enough doses to check: have {}, need dose #{}",
                        immunizations == null ? 0 : immunizations.size(), doseNumber);
                return ValidationResult.NOT_SATISFIED;
            }

            // Get the specified dose (1-indexed, already sorted)
            Immunization dose = immunizations.get(doseNumber - 1);

            // Parse dose date
            LocalDate doseDate;
            try {
                doseDate = LocalDate.parse(dose.getOccurrenceDateTime());
            } catch (Exception e) {
                log.warn("Cannot parse dose date '{}' for condition '{}'",
                        dose.getOccurrenceDateTime(), condition);
                return ValidationResult.UNDETERMINED;
            }

            // Calculate the target birthday
            LocalDate targetDate = birthDate.plusYears(birthdayYear);

            log.debug("Checking if dose date {} is on or after {}th birthday {}",
                    doseDate, birthdayYear, targetDate);

            // Check if dose date is on or after the target birthday
            // CRITICAL: Use !isBefore to include the birthday itself (on or after)
            boolean conditionMet = !doseDate.isBefore(targetDate);

            if (conditionMet) {
                long daysAfter = ChronoUnit.DAYS.between(targetDate, doseDate);
                log.info("✅ Date condition SATISFIED: Dose #{} ({}) given {} days after {}th birthday ({})",
                        doseNumber, doseDate, daysAfter, birthdayYear, targetDate);
                return ValidationResult.SATISFIED;
            } else {
                long daysBefore = ChronoUnit.DAYS.between(doseDate, targetDate);
                log.info("❌ Date condition NOT SATISFIED: Dose #{} ({}) given {} days before {}th birthday ({})",
                        doseNumber, doseDate, daysBefore, birthdayYear, targetDate);
                return ValidationResult.NOT_SATISFIED;
            }

        } catch (NumberFormatException e) {
            log.error("Error parsing numbers in birthday condition '{}': {}", condition, e.getMessage());
            return ValidationResult.UNDETERMINED;
        } catch (Exception e) {
            log.error("Unexpected error evaluating birthday condition '{}': {}", condition, e.getMessage(), e);
            return ValidationResult.UNDETERMINED;
        }
    }

    /**
     * Evaluates a month-based condition.
     *
     * @param matcher Regex matcher containing dose number and month number
     * @param condition Original condition string (for logging)
     * @param immunizations List of immunizations (already sorted)
     * @param birthDate Patient's birth date
     * @return ValidationResult (SATISFIED, NOT_SATISFIED, or UNDETERMINED)
     */
    private ValidationResult evaluateMonthCondition(Matcher matcher,
                                                    String condition,
                                                    List<Immunization> immunizations,
                                                    LocalDate birthDate) {
        try {
            int doseNumber = Integer.parseInt(matcher.group(1));
            int monthsAfterBirth = Integer.parseInt(matcher.group(2));

            log.debug("Parsed MONTH condition - Dose: {}, Months after birth: {}",
                    doseNumber, monthsAfterBirth);

            // Check if we have enough doses
            if (immunizations == null || immunizations.size() < doseNumber) {
                log.debug("Not enough doses to check: have {}, need dose #{}",
                        immunizations == null ? 0 : immunizations.size(), doseNumber);
                return ValidationResult.NOT_SATISFIED;
            }

            // Get the specified dose (1-indexed, already sorted)
            Immunization dose = immunizations.get(doseNumber - 1);

            // Parse dose date
            LocalDate doseDate;
            try {
                doseDate = LocalDate.parse(dose.getOccurrenceDateTime());
            } catch (Exception e) {
                log.warn("Cannot parse dose date '{}' for condition '{}'",
                        dose.getOccurrenceDateTime(), condition);
                return ValidationResult.UNDETERMINED;
            }

            // Calculate the target date (birth date + N months)
            LocalDate targetDate = birthDate.plusMonths(monthsAfterBirth);

            log.debug("Checking if dose date {} is on or after {} months from birth ({})",
                    doseDate, monthsAfterBirth, targetDate);

            // Check if dose date is on or after the target date
            // CRITICAL: Use !isBefore to include the target date itself (on or after)
            boolean conditionMet = !doseDate.isBefore(targetDate);

            if (conditionMet) {
                long daysAfter = ChronoUnit.DAYS.between(targetDate, doseDate);
                log.info("✅ Date condition SATISFIED: Dose #{} ({}) given {} days after {}-month target ({})",
                        doseNumber, doseDate, daysAfter, monthsAfterBirth, targetDate);
                return ValidationResult.SATISFIED;
            } else {
                long daysBefore = ChronoUnit.DAYS.between(doseDate, targetDate);
                log.info("❌ Date condition NOT SATISFIED: Dose #{} ({}) given {} days before {}-month target ({})",
                        doseNumber, doseDate, daysBefore, monthsAfterBirth, targetDate);
                return ValidationResult.NOT_SATISFIED;
            }

        } catch (NumberFormatException e) {
            log.error("Error parsing numbers in month condition '{}': {}", condition, e.getMessage());
            return ValidationResult.UNDETERMINED;
        } catch (Exception e) {
            log.error("Unexpected error evaluating month condition '{}': {}", condition, e.getMessage(), e);
            return ValidationResult.UNDETERMINED;
        }
    }

    /**
     * Safely sorts immunizations by date.
     *
     * This method provides defensive sorting to ensure immunizations are in chronological order
     * regardless of input order. Handles null lists and unparseable dates gracefully.
     *
     * @param immunizations List of immunizations (may be null or unsorted)
     * @return Sorted list of immunizations, or empty list if input is null
     */
    private List<Immunization> sortImmunizationsByDate(List<Immunization> immunizations) {
        if (immunizations == null || immunizations.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            return immunizations.stream()
                    .sorted(Comparator.comparing(Immunization::getOccurrenceDateTime))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            log.warn("Error sorting immunizations by date: {}. Returning unsorted list.", e.getMessage());
            return new ArrayList<>(immunizations);
        }
    }

    /**
     * Convenience method for evaluating a single condition with string birthDate.
     *
     * @param condition Date condition to evaluate
     * @param immunizations List of immunizations (will be sorted internally)
     * @param birthDateStr Birth date as string (ISO format)
     * @return ValidationResult (SATISFIED, NOT_SATISFIED, or UNDETERMINED)
     */
    public ValidationResult evaluateCondition(String condition,
                                              List<Immunization> immunizations,
                                              String birthDateStr) {
        try {
            LocalDate birthDate = LocalDate.parse(birthDateStr);
            return evaluateCondition(condition, immunizations, birthDate);
        } catch (Exception e) {
            log.error("Invalid birth date format: '{}' - cannot evaluate condition '{}'",
                    birthDateStr, condition);
            return ValidationResult.UNDETERMINED;
        }
    }
}