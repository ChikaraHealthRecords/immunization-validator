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
 * IntervalConditionEvaluator for Massachusetts immunization requirements.
 * Validates minimum time intervals between vaccine doses.
 *
 * Version 3.1 - Added support for specific dose-pair interval syntax
 *
 * Supported interval condition patterns:
 *
 * PATTERN A — All consecutive pairs:
 *   "at least X (days|weeks|months|years) between doses"
 *   Example: "at least 28 days between doses"
 *   → Checks EVERY consecutive pair (1→2, 2→3, 3→4 …)
 *   → Used for: MMR, Varicella
 *
 * PATTERN B — Last two doses only:
 *   "at least X (days|weeks|months|years) between last two doses"
 *   Example: "at least 6 months between last two doses"
 *   → Checks only the final consecutive pair
 *   → Use sparingly; prefer Pattern C for clarity
 *
 * PATTERN C — Specific dose pair (NEW in 3.1):
 *   "at least X (days|weeks|months|years) between Nth and Mth dose"
 *   Example: "at least 6 months between 3rd and 4th dose"
 *   Example: "at least 8 weeks between 1st and 2nd dose"
 *   → Checks ONLY the specified pair by 1-based dose index
 *   → Used for: Polio (3rd→4th), MenACWY 11-12 (1st→2nd)
 *   → Preferred over Pattern B — explicit and auditable
 *
 * Why Pattern C matters:
 *   "between last two doses" on a 4-dose Polio series checks pairs 1→2, 2→3, AND 3→4.
 *   The MA DPH regulation only requires ≥6 months between doses 3 and 4.
 *   Pattern C targets exactly what the regulation says, preventing false negatives
 *   on patients with short early dose gaps that are legally acceptable.
 *
 * @author David Saaka
 * @version 3.1 - Specific dose-pair interval support
 */
@Slf4j
@Component
public class IntervalConditionEvaluator {

    /**
     * PATTERN A/B: "at least X unit between (last two )?doses"
     * Group 1: amount (e.g. "28", "6")
     * Group 2: unit   (e.g. "days", "months")
     * Group 3: "last two " if present, null otherwise
     */
    private static final Pattern INTERVAL_PATTERN_GENERIC = Pattern.compile(
            "at least\\s+(\\d+)\\s+(days?|weeks?|months?|years?)\\s+between\\s+(last two\\s+)?doses",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * PATTERN C: "at least X unit between Nth and Mth dose"
     * Group 1: amount     (e.g. "6")
     * Group 2: unit       (e.g. "months")
     * Group 3: from-dose# (e.g. "3")
     * Group 4: to-dose#   (e.g. "4")
     *
     * Supports ordinal suffixes: 1st, 2nd, 3rd, 4th, 5th …
     */
    private static final Pattern INTERVAL_PATTERN_NUMBERED = Pattern.compile(
            "at least\\s+(\\d+)\\s+(days?|weeks?|months?|years?)\\s+between\\s+" +
                    "(\\d+)(?:st|nd|rd|th)\\s+and\\s+(\\d+)(?:st|nd|rd|th)\\s+dose",
            Pattern.CASE_INSENSITIVE
    );

    // ─────────────────────────────────────────────────────────────────────
    // PUBLIC API
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Evaluates multiple interval conditions (AND logic — all must be met).
     *
     * Combination Rules:
     * - ANY NOT_SATISFIED  → NOT_SATISFIED
     * - ANY UNDETERMINED (no NOT_SATISFIED) → UNDETERMINED
     * - ALL SATISFIED     → SATISFIED
     */
    public ValidationResult evaluateConditions(List<String> conditions,
                                               List<Immunization> immunizations) {
        if (conditions == null || conditions.isEmpty()) {
            log.debug("No interval conditions to evaluate");
            return ValidationResult.SATISFIED;
        }

        log.debug("Evaluating {} interval condition(s)", conditions.size());

        List<Immunization> sorted = sortImmunizationsByDate(immunizations);
        List<ValidationResult> results = new ArrayList<>();

        for (String condition : conditions) {
            ValidationResult result = evaluateCondition(condition, sorted);
            results.add(result);

            if (result == ValidationResult.NOT_SATISFIED) {
                log.info("❌ Interval condition NOT satisfied: '{}'", condition);
            } else if (result == ValidationResult.UNDETERMINED) {
                log.warn("⚠️ Interval condition UNDETERMINED: '{}'", condition);
            } else {
                log.debug("✅ Interval condition satisfied: '{}'", condition);
            }
        }

        ValidationResult combined = ValidationResult.and(results.toArray(new ValidationResult[0]));
        log.debug("Combined result of {} interval condition(s): {}", conditions.size(), combined);
        return combined;
    }

    /**
     * Evaluates a single interval condition string.
     *
     * Tries Pattern C (numbered) first, then Pattern A/B (generic).
     * Returns UNDETERMINED if neither pattern matches.
     */
    public ValidationResult evaluateCondition(String condition,
                                              List<Immunization> immunizations) {
        if (condition == null || condition.trim().isEmpty()) {
            log.warn("Empty interval condition provided");
            return ValidationResult.UNDETERMINED;
        }

        log.debug("Evaluating interval condition: '{}'", condition);

        List<Immunization> sorted = sortImmunizationsByDate(immunizations);

        // ── Try Pattern C first: "between Nth and Mth dose" ──────────────
        Matcher numberedMatcher = INTERVAL_PATTERN_NUMBERED.matcher(condition);
        if (numberedMatcher.find()) {
            try {
                int amount   = Integer.parseInt(numberedMatcher.group(1));
                String unit  = numberedMatcher.group(2).toLowerCase();
                int fromDose = Integer.parseInt(numberedMatcher.group(3)); // 1-based
                int toDose   = Integer.parseInt(numberedMatcher.group(4)); // 1-based

                log.debug("Parsed NUMBERED interval — amount: {}, unit: {}, from dose #{}, to dose #{}",
                        amount, unit, fromDose, toDose);

                return checkSpecificDosePair(amount, unit, fromDose, toDose, sorted, condition);

            } catch (NumberFormatException e) {
                log.error("Error parsing numbered interval condition '{}': {}", condition, e.getMessage());
                return ValidationResult.UNDETERMINED;
            }
        }

        // ── Try Pattern A/B: "between (last two )?doses" ─────────────────
        Matcher genericMatcher = INTERVAL_PATTERN_GENERIC.matcher(condition);
        if (genericMatcher.find()) {
            try {
                int amount          = Integer.parseInt(genericMatcher.group(1));
                String unit         = genericMatcher.group(2).toLowerCase();
                boolean lastTwoOnly = (genericMatcher.group(3) != null);

                log.debug("Parsed GENERIC interval — amount: {}, unit: {}, lastTwoOnly: {}",
                        amount, unit, lastTwoOnly);

                if (sorted == null || sorted.size() < 2) {
                    log.debug("Not enough doses to check intervals: have {}, need at least 2",
                            sorted == null ? 0 : sorted.size());
                    return ValidationResult.NOT_SATISFIED;
                }

                return lastTwoOnly
                        ? checkLastTwoDoses(amount, unit, sorted)
                        : checkAllConsecutivePairs(amount, unit, sorted);

            } catch (NumberFormatException e) {
                log.error("Error parsing generic interval condition '{}': {}", condition, e.getMessage());
                return ValidationResult.UNDETERMINED;
            }
        }

        // ── No pattern matched ────────────────────────────────────────────
        log.warn("Could not parse interval condition: '{}'. Supported patterns:\n" +
                        "  A: 'at least X (days|weeks|months|years) between doses'\n" +
                        "  B: 'at least X (days|weeks|months|years) between last two doses'\n" +
                        "  C: 'at least X (days|weeks|months|years) between Nth and Mth dose'",
                condition);
        return ValidationResult.UNDETERMINED;
    }

    // ─────────────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────────────

    /**
     * PATTERN C — Checks the interval between a specific pair of doses by 1-based index.
     * Example: fromDose=3, toDose=4 checks doses[2] → doses[3]
     */
    private ValidationResult checkSpecificDosePair(int amount, String unit,
                                                   int fromDose, int toDose,
                                                   List<Immunization> immunizations,
                                                   String condition) {
        // Convert to 0-based index
        int fromIdx = fromDose - 1;
        int toIdx   = toDose   - 1;

        if (immunizations == null || immunizations.size() <= toIdx) {
            log.debug("Not enough doses to check interval between dose #{} and #{}: only {} dose(s) present",
                    fromDose, toDose, immunizations == null ? 0 : immunizations.size());
            return ValidationResult.NOT_SATISFIED;
        }

        LocalDate fromDate;
        LocalDate toDate;
        try {
            fromDate = LocalDate.parse(immunizations.get(fromIdx).getOccurrenceDateTime());
            toDate   = LocalDate.parse(immunizations.get(toIdx).getOccurrenceDateTime());
        } catch (Exception e) {
            log.warn("Cannot parse dates for dose #{} or #{} — condition: '{}'",
                    fromDose, toDose, condition);
            return ValidationResult.UNDETERMINED;
        }

        long actual   = calculateInterval(fromDate, toDate, unit);
        if (actual < 0) {
            return ValidationResult.UNDETERMINED;
        }

        if (actual < amount) {
            log.info("❌ Interval NOT SATISFIED: dose #{} to #{} = {} {} (required ≥ {} {})",
                    fromDose, toDose, actual, unit, amount, unit);
            return ValidationResult.NOT_SATISFIED;
        }

        log.info("✅ Interval SATISFIED: dose #{} to #{} = {} {} (required ≥ {} {})",
                fromDose, toDose, actual, unit, amount, unit);
        return ValidationResult.SATISFIED;
    }

    /**
     * PATTERN B — Checks only the interval between the last two doses.
     */
    private ValidationResult checkLastTwoDoses(int amount, String unit,
                                               List<Immunization> immunizations) {
        int size = immunizations.size();
        Immunization prev = immunizations.get(size - 2);
        Immunization last = immunizations.get(size - 1);

        LocalDate prevDate;
        LocalDate lastDate;
        try {
            prevDate = LocalDate.parse(prev.getOccurrenceDateTime());
            lastDate = LocalDate.parse(last.getOccurrenceDateTime());
        } catch (Exception e) {
            log.warn("Cannot parse dose dates for last two doses: prev='{}', curr='{}'",
                    prev.getOccurrenceDateTime(), last.getOccurrenceDateTime());
            return ValidationResult.UNDETERMINED;
        }

        long actual = calculateInterval(prevDate, lastDate, unit);
        if (actual < 0) return ValidationResult.UNDETERMINED;

        if (actual < amount) {
            log.info("❌ Interval NOT SATISFIED (last two doses): {} {} (required ≥ {} {})",
                    actual, unit, amount, unit);
            return ValidationResult.NOT_SATISFIED;
        }

        log.info("✅ Interval SATISFIED (last two doses): {} {} (required ≥ {} {})",
                actual, unit, amount, unit);
        return ValidationResult.SATISFIED;
    }

    /**
     * PATTERN A — Checks intervals between ALL consecutive dose pairs.
     * Returns NOT_SATISFIED if ANY pair fails the required interval.
     */
    private ValidationResult checkAllConsecutivePairs(int amount, String unit,
                                                      List<Immunization> immunizations) {
        for (int i = 1; i < immunizations.size(); i++) {
            LocalDate prev;
            LocalDate curr;
            try {
                prev = LocalDate.parse(immunizations.get(i - 1).getOccurrenceDateTime());
                curr = LocalDate.parse(immunizations.get(i).getOccurrenceDateTime());
            } catch (Exception e) {
                log.warn("Cannot parse dose dates at index {}: prev='{}', curr='{}'",
                        i, immunizations.get(i - 1).getOccurrenceDateTime(),
                        immunizations.get(i).getOccurrenceDateTime());
                return ValidationResult.UNDETERMINED;
            }

            long actual = calculateInterval(prev, curr, unit);
            if (actual < 0) return ValidationResult.UNDETERMINED;

            if (actual < amount) {
                log.info("❌ Interval NOT SATISFIED: dose {} to {} = {} {} (required ≥ {} {})",
                        i, i + 1, actual, unit, amount, unit);
                return ValidationResult.NOT_SATISFIED;
            }

            log.debug("Interval OK: dose {} to {} = {} {} (required ≥ {} {})",
                    i, i + 1, actual, unit, amount, unit);
        }

        log.info("✅ Interval condition SATISFIED: All {} dose pair(s) have ≥ {} {} between them",
                immunizations.size() - 1, amount, unit);
        return ValidationResult.SATISFIED;
    }

    /**
     * Calculates the interval between two dates in the specified time unit.
     * Returns -1 for unsupported units.
     */
    private long calculateInterval(LocalDate startDate, LocalDate endDate, String unit) {
        String u = unit.endsWith("s") ? unit.substring(0, unit.length() - 1) : unit;
        switch (u.toLowerCase()) {
            case "day":   return ChronoUnit.DAYS.between(startDate, endDate);
            case "week":  return ChronoUnit.WEEKS.between(startDate, endDate);
            case "month": return ChronoUnit.MONTHS.between(startDate, endDate);
            case "year":  return ChronoUnit.YEARS.between(startDate, endDate);
            default:
                log.error("Unsupported time unit: '{}'", unit);
                return -1;
        }
    }

    /**
     * Safely sorts immunizations chronologically by occurrenceDateTime.
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
}