package com.immunization.validator.service;

import com.immunization.validator.model.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.Comparator;

/**
 * Service for validating patient immunization records against state requirements.
 *
 * Version 3.0 includes:
 * - Tri-state validation: VALID, INVALID, UNDETERMINED
 * - Date-based condition validation (1st, 4th, 10th, 16th birthdays and months)
 * - Interval-based condition validation (28 days, 6 months, 8 weeks)
 * - Alternate requirements support with FLEXIBLE or STRICT modes
 * - Medical exemptions support
 * - Detailed error reporting and metadata
 * - CVX code translation via database lookup (Version 3.1)
 *
 * Alternate Requirement Behavior:
 * - FLEXIBLE mode (default): If alternate fails, check primary requirement
 *   Example: 4 DTaP doses with 4th dose too early → Check if has 5 doses
 * - STRICT mode: If alternate fails, mark as INVALID without checking primary
 *   Example: 4 DTaP doses with 4th dose too early → INVALID immediately
 *
 * @author Saakad
 * @since 2026-01-12
 * @version 3.1 - Added CVX code translation support
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ValidationService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final String VALIDATOR_VERSION = "3.1.0";

    private final RequirementsService requirementsService;
    private final DateConditionEvaluator dateConditionEvaluator;
    private final IntervalConditionEvaluator intervalConditionEvaluator;
    private final VaccineLookupService vaccineLookupService;

    /**
     * Alternate requirement behavior mode.
     * FLEXIBLE: Check primary if alternate fails (default, more forgiving)
     * STRICT: Mark invalid if alternate fails (don't check primary)
     */
    @Value("${immunization.validation.alternate-behavior:FLEXIBLE}")
    private String alternateBehaviorMode;

    /**
     * Validate a patient's immunization record against state requirements.
     *
     * @param patient Patient with immunization records
     * @param stateCode State code (e.g., "CA", "MA")
     * @param age Patient's age in years (optional if birthDate provided)
     * @param schoolYear School year (e.g., "preschool", "K-6") - optional, alternative to age
     * @param includeDetails Whether to include unmet requirements and undetermined conditions in response
     * @return ValidationResponse with tri-state ComplianceStatus
     */
    public ValidationResponse validate(Patient patient, String stateCode, Integer age,
                                       String schoolYear, boolean includeDetails) {

        String maskedId = maskPatientId(patient.getId());
        log.info("Validating patient {} against {} requirements (mode: {})",
                maskedId, stateCode, alternateBehaviorMode);

        // Calculate age from birthDate if not provided
        Integer effectiveAge = age;
        if (effectiveAge == null && patient.getBirthDate() != null) {
            effectiveAge = calculateAge(patient.getBirthDate());
        }

        // Get requirements for the state and age/school year
        List<ValidationRequirement> requirements;
        if (schoolYear != null) {
            requirements = requirementsService.getRequirements(stateCode, schoolYear);
        } else {
            requirements = requirementsService.getRequirements(stateCode, effectiveAge);
        }

        if (requirements == null || requirements.isEmpty()) {
            log.warn("No requirements found for state: {}, age: {}, schoolYear: {}",
                    stateCode, effectiveAge, schoolYear);

            ValidationResponse noReqResponse = ValidationResponse.builder()
                    .patientId(patient.getId())
                    .status(ComplianceStatus.UNDETERMINED)
                    .message("No validation requirements found for the specified criteria")
                    .undeterminedConditions(List.of(
                            UndeterminedCondition.builder()
                                    .reason("No requirements found")
                                    .details(String.format("State: %s, SchoolYear: %s, Age: %s",
                                            stateCode, schoolYear, effectiveAge))
                                    .suggestion("Verify state code and school year are correct")
                                    .build()
                    ))
                    .build();
            noReqResponse.setStatusWithBackwardCompatibility(ComplianceStatus.UNDETERMINED);
            return noReqResponse;
        }

        // Validate requirements
        InternalValidationResult validationResult = validateRequirements(patient, requirements);

        // Build response based on result
        ValidationResponse response = buildResponse(
                patient.getId(),
                validationResult,
                stateCode,
                schoolYear,
                requirements.size(),
                includeDetails
        );

        log.info("Validation complete for patient {}: {}", maskedId, response.getStatus());

        return response;
    }

    /**
     * Translate CVX codes to vaccine group names for all immunizations.
     * If an immunization has cvxCode but no vaccineCode, look up the vaccine groups
     * from the database and expand into multiple immunizations (for combo vaccines).
     *
     * Example: CVX 110 (Pediarix) → expands to 3 immunizations: DTaP, HepB, Polio
     *
     * @param immunizations Original list of immunizations
     * @return Expanded list with CVX codes translated to vaccine group names
     */
    private List<Immunization> translateCvxCodes(List<Immunization> immunizations) {
        if (immunizations == null || immunizations.isEmpty()) {
            return immunizations;
        }

        List<Immunization> translatedList = new ArrayList<>();

        for (Immunization imm : immunizations) {
            // If vaccineCode is already set, keep it as-is
            if (imm.getVaccineCode() != null && !imm.getVaccineCode().isEmpty()) {
                translatedList.add(imm);
                continue;
            }

            // If cvxCode is set, translate it
            if (imm.getCvxCode() != null) {
                List<String> vaccineGroups = vaccineLookupService.getVaccineGroupsForCvx(imm.getCvxCode());

                if (vaccineGroups.isEmpty()) {
                    log.warn("No vaccine groups found for CVX code: {}", imm.getCvxCode());
                    // Keep original immunization with null vaccineCode (will fail validation)
                    translatedList.add(imm);
                } else {
                    // Expand into multiple immunizations (for combo vaccines)
                    // CVX 110 → DTaP, HepB, Polio (3 separate immunizations)
                    for (String group : vaccineGroups) {
                        Immunization translated = new Immunization();
                        translated.setVaccineCode(vaccineCodeForGroup(imm.getCvxCode(), group));
                        translated.setCvxCode(imm.getCvxCode());
                        translated.setOccurrenceDateTime(imm.getOccurrenceDateTime());
                        translated.setLotNumber(imm.getLotNumber());
                        translatedList.add(translated);
                    }
                    log.debug("CVX {} → {} vaccine groups: {}",
                            imm.getCvxCode(), vaccineGroups.size(), vaccineGroups);
                }
            } else {
                // Neither cvxCode nor vaccineCode set - invalid data
                log.warn("Immunization has neither cvxCode nor vaccineCode");
                translatedList.add(imm);
            }
        }


        return translatedList;
    }

    /**
     * Removes doses with no vaccine code after CVX translation, adding a
     * warning for each so the person knows they weren't counted.
     */
    private List<Immunization> withoutUnidentifiedDoses(List<Immunization> immunizations,
                                                        InternalValidationResult result) {
        List<Immunization> identified = new ArrayList<>();
        Set<Integer> unknownCvx = new TreeSet<>();
        int withoutAnyCode = 0;
        for (Immunization imm : immunizations) {
            if (imm.getVaccineCode() != null && !imm.getVaccineCode().isBlank()) {
                identified.add(imm);
            } else if (imm.getCvxCode() != null) {
                unknownCvx.add(imm.getCvxCode());
            } else {
                withoutAnyCode++;
            }
        }
        for (Integer cvx : unknownCvx) {
            result.warnings.add(String.format(
                    "CVX code %d isn't in the vaccine list, so doses with that code weren't counted.", cvx));
        }
        if (withoutAnyCode > 0) {
            result.warnings.add(String.format(
                    "%d dose%s had no vaccine code or CVX code and %s counted.",
                    withoutAnyCode, withoutAnyCode == 1 ? "" : "s", withoutAnyCode == 1 ? "wasn't" : "weren't"));
        }
        return identified;
    }

    /**
     * The rule vaccine code for one CVX code's vaccine group. Mostly a rename
     * (DTAP -> DTaP), but CDC's MENING group mixes MenACWY with vaccines that
     * don't meet a MenACWY requirement, so it's decided by CVX code.
     */
    private String vaccineCodeForGroup(Integer cvxCode, String group) {
        if (CvxGroupRules.MENING_GROUP.equalsIgnoreCase(group)) {
            String code = CvxGroupRules.menACWYCodeFor(cvxCode);
            // Other MENING vaccines (MPSV4, MenC, Hib-MenCY) keep the group name,
            // so they count toward no requirement.
            return code != null ? code : group;
        }
        return normalizeVaccineName(group);
    }

    /**
     * De-duplicate immunizations that share the same vaccineCode and date.
     * When duplicates exist, keep the record that has a lot number (verified dose).
     * Records without a lot number are never disqualified on their own — only
     * dropped when a duplicate with a lot number exists for the same vaccine+date.
     */
    private List<Immunization> deduplicateImmunizations(List<Immunization> immunizations) {
        if (immunizations == null || immunizations.size() <= 1) {
            return immunizations;
        }

        // Key: vaccineCode + calendar day (not the raw text), so "2024-01-10" and
        // "2024-01-10T09:00:00" count as the same dose.
        Map<String, Immunization> best = new LinkedHashMap<>();

        for (Immunization imm : immunizations) {
            LocalDate day = ImmunizationDates.parse(imm.getOccurrenceDateTime());
            String dayKey = day != null
                    ? day.toString()
                    : (imm.getOccurrenceDateTime() != null ? imm.getOccurrenceDateTime() : "");
            String key = (imm.getVaccineCode() != null ? imm.getVaccineCode() : "") + "|" + dayKey;

            Immunization existing = best.get(key);
            if (existing == null) {
                best.put(key, imm);
            } else {
                boolean existingHasLot = existing.getLotNumber() != null && !existing.getLotNumber().isBlank();
                boolean newHasLot = imm.getLotNumber() != null && !imm.getLotNumber().isBlank();

                if (!existingHasLot && newHasLot) {
                    // Replace: prefer the one with a lot number
                    best.put(key, imm);
                    log.debug("De-dup {}: replaced record without lot number with lot# {}",
                            key, imm.getLotNumber());
                } else if (existingHasLot || !newHasLot) {
                    log.debug("De-dup {}: keeping existing record (lot# {})",
                            key, existing.getLotNumber());
                }
            }
        }

        int removed = immunizations.size() - best.size();
        if (removed > 0) {
            log.info("De-duplicated immunizations: {} duplicates removed, {} unique records remain",
                    removed, best.size());
        }

        return new ArrayList<>(best.values());
    }

    /**
     * Internal result object for validation tracking.
     * Tracks satisfied, unsatisfied, and undetermined requirements.
     */
    private static class InternalValidationResult {
        List<UnmetRequirement> unmetRequirements = new ArrayList<>();
        List<UndeterminedCondition> undeterminedConditions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int satisfiedCount = 0;
        int unsatisfiedCount = 0;
        int undeterminedCount = 0;

        // Enhanced tracking for alternate requirements
        int alternateAttemptedCount = 0;
        int alternateSatisfiedCount = 0;
        int primaryFallbackCount = 0;
    }

    /**
     * The outcome of evaluating one requirement: satisfied, unmet (with
     * details) or undetermined (with an explanation).
     */
    private static final class RequirementOutcome {
        private final ValidationResult result;
        private final UnmetRequirement unmet;
        private final UndeterminedCondition undetermined;

        private RequirementOutcome(ValidationResult result, UnmetRequirement unmet, UndeterminedCondition undetermined) {
            this.result = result;
            this.unmet = unmet;
            this.undetermined = undetermined;
        }

        static RequirementOutcome satisfied() {
            return new RequirementOutcome(ValidationResult.SATISFIED, null, null);
        }

        static RequirementOutcome unmet(UnmetRequirement unmet) {
            return new RequirementOutcome(ValidationResult.NOT_SATISFIED, unmet, null);
        }

        static RequirementOutcome undetermined(UndeterminedCondition condition) {
            return new RequirementOutcome(ValidationResult.UNDETERMINED, null, condition);
        }

        boolean isSatisfied() {
            return result == ValidationResult.SATISFIED;
        }

        void recordInto(InternalValidationResult target) {
            switch (result) {
                case SATISFIED -> target.satisfiedCount++;
                case NOT_SATISFIED -> {
                    target.unsatisfiedCount++;
                    target.unmetRequirements.add(unmet);
                }
                default -> {
                    target.undeterminedCount++;
                    target.undeterminedConditions.add(undetermined);
                }
            }
        }
    }

    /**
     * Validate patient's immunizations against a list of requirements.
     *
     * This method evaluates:
     * 1. Dose counts (minimum required doses)
     * 2. Date-based conditions (doses on or after specific birthdays)
     * 3. Interval-based conditions (minimum time between doses)
     * 4. Alternate requirements (with their own date/interval conditions)
     * 5. Medical exemptions
     *
     * @param patient Patient with immunization records
     * @param requirements List of validation requirements
     * @return InternalValidationResult with tri-state tracking
     */
    private InternalValidationResult validateRequirements(Patient patient,
                                                          List<ValidationRequirement> requirements) {
        InternalValidationResult result = new InternalValidationResult();

        // Initialize empty list instead of early return
        List<Immunization> patientImmunizations = patient.getImmunizations();
        if (patientImmunizations == null) {
            patientImmunizations = List.of();
        }

        // ✅ ADDED: Translate CVX codes to vaccine group names
        patientImmunizations = translateCvxCodes(patientImmunizations);

        // De-duplicate: same vaccineCode + date = one dose, prefer the record with a lot number
        patientImmunizations = deduplicateImmunizations(patientImmunizations);

        // Doses we couldn't identify (a CVX code that isn't in the vaccine list, or
        // no code at all) can't count toward anything. Say so, and leave them out
        // rather than failing the whole record.
        patientImmunizations = withoutUnidentifiedDoses(patientImmunizations, result);

        // Group full immunization objects for dose counts and date/interval condition checking
        Map<String, List<Immunization>> immunizationsByVaccine = patientImmunizations.stream()
                .collect(Collectors.groupingBy(Immunization::getVaccineCode));

        // Build map of exceptions by vaccine code
        Map<String, String> exceptionsByVaccine = new HashMap<>();
        if (patient.getExceptions() != null) {
            for (VaccineException exception : patient.getExceptions()) {
                exceptionsByVaccine.put(exception.getVaccineCode(), exception.getExceptionType());
            }
        }

        // Parse patient birth date once for all condition evaluations
        LocalDate birthDate = null;
        if (patient.getBirthDate() != null) {
            try {
                birthDate = LocalDate.parse(patient.getBirthDate(), DATE_FORMATTER);
                log.debug("Parsed birth date: {}", birthDate);
            } catch (DateTimeParseException e) {
                log.warn("Invalid birth date format for patient {}: {}",
                        patient.getId(), patient.getBirthDate());
                result.warnings.add("Invalid birth date format - date-based conditions cannot be evaluated");
            }
        }

        // Check if we're in strict mode
        boolean isStrictMode = "STRICT".equalsIgnoreCase(alternateBehaviorMode);

        // Check each requirement
        for (ValidationRequirement requirement : requirements) {
            RequirementOutcome outcome = evaluateWithConditionalDoses(
                    requirement, immunizationsByVaccine, exceptionsByVaccine, birthDate, isStrictMode, result);
            outcome.recordInto(result);
        }

        // Log summary of alternate usage
        if (result.alternateAttemptedCount > 0) {
            log.info("📊 Alternate Requirements Summary: Attempted={}, Satisfied={}, Fell back to Primary={}",
                    result.alternateAttemptedCount, result.alternateSatisfiedCount, result.primaryFallbackCount);
        }

        return result;
    }

    /**
     * Evaluates a requirement, allowing for doses that count only in some
     * circumstances (pediatric DT toward DTaP; see {@link CvxGroupRules}).
     *
     * - Met without those doses: satisfied, as if they weren't there.
     * - Met only by counting them: undetermined ("needs review"), explaining why.
     * - Not met even counting them: the normal unmet result, plus a warning.
     */
    private RequirementOutcome evaluateWithConditionalDoses(ValidationRequirement requirement,
                                                            Map<String, List<Immunization>> immunizationsByVaccine,
                                                            Map<String, String> exceptionsByVaccine,
                                                            LocalDate birthDate,
                                                            boolean isStrictMode,
                                                            InternalValidationResult result) {
        String vaccineCode = requirement.getVaccineCode();
        List<Immunization> doses = immunizationsByVaccine.getOrDefault(vaccineCode, List.of());
        List<Immunization> conditional = CvxGroupRules.conditionalDoses(vaccineCode, doses);
        if (conditional.isEmpty()) {
            return evaluateRequirement(requirement, immunizationsByVaccine, exceptionsByVaccine,
                    birthDate, isStrictMode, result);
        }

        // First without the conditional doses. Use a scratch result so warnings
        // aren't recorded twice if the second pass is needed.
        Map<String, List<Immunization>> withoutConditional = new HashMap<>(immunizationsByVaccine);
        List<Immunization> remaining = doses.stream().filter(d -> !conditional.contains(d)).toList();
        withoutConditional.put(vaccineCode, remaining);
        InternalValidationResult scratch = new InternalValidationResult();
        RequirementOutcome strict = evaluateRequirement(requirement, withoutConditional, exceptionsByVaccine,
                birthDate, isStrictMode, scratch);
        if (strict.isSatisfied()) {
            result.warnings.addAll(scratch.warnings);
            return strict;
        }

        RequirementOutcome counted = evaluateRequirement(requirement, immunizationsByVaccine, exceptionsByVaccine,
                birthDate, isStrictMode, result);
        if (counted.isSatisfied()) {
            log.info("{} met only by counting {} pediatric DT dose(s): needs review", vaccineCode, conditional.size());
            int required = requirement.getMinDoses() != null ? requirement.getMinDoses() : 1;
            return RequirementOutcome.undetermined(CvxGroupRules.dtNeedsReview(
                    requirement.getDescription(), conditional.size(), remaining.size(), required));
        }
        result.warnings.add(CvxGroupRules.dtWarning(conditional.size()));
        return counted;
    }

    /**
     * Evaluates one requirement and returns its outcome without recording it.
     * Warnings and the alternate-usage counters go to {@code result}.
     */
    private RequirementOutcome evaluateRequirement(ValidationRequirement requirement,
                                                   Map<String, List<Immunization>> immunizationsByVaccine,
                                                   Map<String, String> exceptionsByVaccine,
                                                   LocalDate birthDate,
                                                   boolean isStrictMode,
                                                   InternalValidationResult result) {
        Map<String, Long> vaccineCounts = new HashMap<>();
        immunizationsByVaccine.forEach((code, doses) -> vaccineCounts.put(code, (long) doses.size()));

        String vaccineCode = requirement.getVaccineCode();

        // ========================================
        // STEP 1: Check exceptions FIRST
        // ========================================
        if (requirement.getAcceptedExceptions() != null &&
                !requirement.getAcceptedExceptions().isEmpty()) {

            String exceptionType = exceptionsByVaccine.get(vaccineCode);
            if (exceptionType != null &&
                    requirement.getAcceptedExceptions().contains(exceptionType)) {

                log.debug("✅ Requirement satisfied by exception {} for vaccine {}",
                        exceptionType, vaccineCode);
                return RequirementOutcome.satisfied();
            }
        }

        Long foundDoses = vaccineCounts.getOrDefault(vaccineCode, 0L);
        Integer requiredDoses = requirement.getMinDoses() != null ? requirement.getMinDoses() : 1;

        // ========================================
        // STEP 2: Check alternate requirements
        // ========================================
        boolean alternateRequirementMet = false;
        boolean attemptedAlternate = false; // Track if alternate was attempted
        ValidationResult alternateResult = null;

        if (requirement.getAlternateRequirements() != null &&
                !requirement.getAlternateRequirements().isEmpty()) {

            for (AlternateRequirement altReq : requirement.getAlternateRequirements()) {
                String altVaccineCode = altReq.getAlternateVaccineCode() != null ?
                        altReq.getAlternateVaccineCode() : vaccineCode;
                Integer altRequiredDoses = altReq.getMinDoses() != null ? altReq.getMinDoses() : 1;
                Long altFoundDoses = vaccineCounts.getOrDefault(altVaccineCode, 0L);

                // Check dose count first
                if (altFoundDoses >= altRequiredDoses) {
                    attemptedAlternate = true; // Patient has minimum doses for alternate
                    result.alternateAttemptedCount++;

                    // Get immunizations for this vaccine, sorted chronologically
                    List<Immunization> vaccineImmunizations = immunizationsByVaccine
                            .getOrDefault(altVaccineCode, new ArrayList<>())
                            .stream()
                            .sorted(Comparator.comparing(Immunization::getOccurrenceDateTime))
                            .collect(Collectors.toList());

                    // Evaluate date-based condition if present
                    ValidationResult dateConditionResult = ValidationResult.SATISFIED;

                    if (altReq.getDateConditions() != null && !altReq.getDateConditions().isEmpty()) {
                        if (birthDate != null) {
                            dateConditionResult = dateConditionEvaluator.evaluateConditions(
                                    altReq.getDateConditions(),
                                    vaccineImmunizations,
                                    birthDate
                            );
                            if (dateConditionResult != ValidationResult.SATISFIED) {
                                log.debug("📋 Alternate date condition NOT satisfied for {}: '{}'",
                                        vaccineCode, altReq.getCondition());
                            }
                        } else {
                            dateConditionResult = ValidationResult.UNDETERMINED;
                            result.warnings.add(String.format(
                                    "Cannot evaluate date conditions for %s alternate - missing birth date", vaccineCode));
                        }
                    }

                    // Evaluate interval-based conditions if present
                    ValidationResult intervalConditionResult = ValidationResult.SATISFIED;

                    if (altReq.getIntervalConditions() != null && !altReq.getIntervalConditions().isEmpty()) {
                        intervalConditionResult = intervalConditionEvaluator.evaluateConditions(
                                altReq.getIntervalConditions(),
                                vaccineImmunizations
                        );
                        if (intervalConditionResult != ValidationResult.SATISFIED) {
                            log.debug("📋 Alternate interval condition NOT satisfied for {}", vaccineCode);
                        }
                    }

                    // Both date and interval conditions must be met
                    alternateResult = ValidationResult.and(dateConditionResult, intervalConditionResult);

                    if (alternateResult == ValidationResult.SATISFIED) {
                        log.info("✅ {} satisfied via ALTERNATE requirement: {} doses with all conditions met",
                                vaccineCode, altFoundDoses);
                        alternateRequirementMet = true;
                        result.alternateSatisfiedCount++;
                        break;
                    } else if (alternateResult == ValidationResult.UNDETERMINED) {
                        log.debug("⚠️ Alternate requirement UNDETERMINED for {}", vaccineCode);
                        // Continue checking other alternates
                    } else {
                        log.debug("❌ Alternate attempted but NOT satisfied for {}", vaccineCode);
                        // Continue checking other alternates
                    }
                }
            }
        }

        // Check if alternate was satisfied
        if (alternateRequirementMet) {
            return RequirementOutcome.satisfied();
        }

        // ========================================
        // STEP 2.5: Handle STRICT mode
        // ========================================
        if (isStrictMode && attemptedAlternate && !alternateRequirementMet) {
            // STRICT MODE: If alternate was attempted but failed, mark as INVALID
            // Don't check primary requirement
            log.warn("⚠️ STRICT MODE: {} attempted alternate but failed - marking INVALID without checking primary",
                    vaccineCode);
            return RequirementOutcome.unmet(UnmetRequirement.builder()
                    .description(String.format("Alternate requirement not satisfied (STRICT mode): %s",
                            requirement.getDescription()))
                    .vaccineCode(vaccineCode)
                    .requiredDoses(requiredDoses)
                    .foundDoses(foundDoses.intValue())
                    .build());
        }

        // ========================================
        // STEP 3: Check primary requirement (FLEXIBLE mode or no alternate attempted)
        // ========================================

        if (attemptedAlternate && !alternateRequirementMet) {
            log.info("📋 {} - Alternate attempted but not satisfied, checking PRIMARY requirement (FLEXIBLE mode)",
                    vaccineCode);
            result.primaryFallbackCount++;
        }

        // Get immunizations for this vaccine, sorted chronologically
        List<Immunization> vaccineImmunizations = immunizationsByVaccine
                .getOrDefault(vaccineCode, new ArrayList<>())
                .stream()
                .sorted(Comparator.comparing(Immunization::getOccurrenceDateTime))
                .collect(Collectors.toList());

        // Check dose count
        if (foundDoses >= requiredDoses) {
            // Have enough doses, now check conditions

            // Evaluate date conditions if present
            ValidationResult dateResult = ValidationResult.SATISFIED;

            if (requirement.getDateConditions() != null && !requirement.getDateConditions().isEmpty()) {
                if (birthDate != null) {
                    dateResult = dateConditionEvaluator.evaluateConditions(
                            requirement.getDateConditions(),
                            vaccineImmunizations,
                            birthDate
                    );
                    if (dateResult != ValidationResult.SATISFIED) {
                        log.debug("Date conditions not met for vaccine {}", vaccineCode);
                    }
                } else {
                    dateResult = ValidationResult.UNDETERMINED;
                    result.warnings.add(String.format(
                            "Cannot evaluate date conditions for %s - missing birth date", vaccineCode));
                }
            }

            // Evaluate interval conditions if present
            ValidationResult intervalResult = ValidationResult.SATISFIED;

            if (requirement.getIntervalConditions() != null &&
                    !requirement.getIntervalConditions().isEmpty()) {
                intervalResult = intervalConditionEvaluator.evaluateConditions(
                        requirement.getIntervalConditions(),
                        vaccineImmunizations
                );
                if (intervalResult != ValidationResult.SATISFIED) {
                    log.debug("Interval conditions not met for vaccine {}", vaccineCode);
                }
            }

            // Combine results using AND logic
            ValidationResult primaryResult = ValidationResult.and(dateResult, intervalResult);

            if (primaryResult == ValidationResult.SATISFIED) {
                if (attemptedAlternate) {
                    log.info("✅ {} satisfied via PRIMARY requirement (alternate was attempted but failed): {} doses",
                            vaccineCode, foundDoses);
                } else {
                    log.info("✅ {} satisfied via PRIMARY requirement: {} doses with all conditions met",
                            vaccineCode, foundDoses);
                }
                return RequirementOutcome.satisfied();
            } else if (primaryResult == ValidationResult.UNDETERMINED) {
                // Cannot evaluate - add to undetermined
                return RequirementOutcome.undetermined(UndeterminedCondition.builder()
                        .vaccineCode(vaccineCode)
                        .condition(requirement.getDescription())
                        .reason("Cannot evaluate date or interval conditions")
                        .details(String.format("Missing or invalid data for %s validation", vaccineCode))
                        .suggestion("Verify birth date is provided and conditions are correctly formatted")
                        .build());
            } else {
                // NOT_SATISFIED - have doses but conditions not met

                String description;
                if (attemptedAlternate) {
                    description = String.format("%s: %d doses found, but neither alternate nor primary requirements satisfied. %s",
                            vaccineCode, foundDoses.intValue(),
                            requirement.getDescription() != null ? requirement.getDescription() : "");
                } else {
                    description = String.format("%s: %d doses found, but date/interval conditions not met. %s",
                            vaccineCode, foundDoses.intValue(),
                            requirement.getDescription() != null ? requirement.getDescription() : "");
                }

                return RequirementOutcome.unmet(UnmetRequirement.builder()
                        .description(description)
                        .vaccineCode(vaccineCode)
                        .requiredDoses(requiredDoses)
                        .foundDoses(foundDoses.intValue())
                        .build());
            }
        }

        // Not enough doses
        return RequirementOutcome.unmet(UnmetRequirement.builder()
                .description(requirement.getDescription() != null ?
                        requirement.getDescription() :
                        String.format("Insufficient doses of %s: required %d, found %d",
                                vaccineCode, requiredDoses, foundDoses))
                .vaccineCode(vaccineCode)
                .requiredDoses(requiredDoses)
                .foundDoses(foundDoses.intValue())
                .build());
    }

    /**
     * Build the final ValidationResponse with tri-state status.
     *
     * Determines overall status based on:
     * - UNDETERMINED: If any requirement cannot be evaluated
     * - INVALID: If all requirements can be evaluated but some are not satisfied
     * - VALID: If all requirements are satisfied
     *
     * @param patientId Patient identifier
     * @param result Internal validation result with counts
     * @param state State code
     * @param schoolYear School year
     * @param totalRequirements Total number of requirements checked
     * @param includeDetails Whether to include detailed lists in response
     * @return Complete ValidationResponse
     */
    private ValidationResponse buildResponse(String patientId,
                                             InternalValidationResult result,
                                             String state,
                                             String schoolYear,
                                             int totalRequirements,
                                             boolean includeDetails) {

        // Determine overall status
        ComplianceStatus status;
        String message;

        if (result.undeterminedCount > 0) {
            // If ANY requirement is undetermined, overall status is UNDETERMINED
            status = ComplianceStatus.UNDETERMINED;
            message = String.format("Cannot determine compliance: %d requirement(s) could not be evaluated",
                    result.undeterminedCount);
        } else if (result.unsatisfiedCount > 0) {
            // All requirements determined, but some not satisfied
            status = ComplianceStatus.INVALID;
            message = String.format("Patient does not meet requirements: %d requirement(s) not satisfied",
                    result.unsatisfiedCount);
        } else {
            // All requirements satisfied
            status = ComplianceStatus.VALID;
            message = "Patient meets all immunization requirements";
        }

        ValidationResponse.ValidationResponseBuilder responseBuilder = ValidationResponse.builder()
                .patientId(patientId)
                .status(status)
                .message(message)
                .metadata(ValidationMetadata.builder()
                        .validatedAt(LocalDateTime.now())
                        .state(state)
                        .schoolYear(schoolYear)
                        .totalRequirements(totalRequirements)
                        .satisfiedRequirements(result.satisfiedCount)
                        .unsatisfiedRequirements(result.unsatisfiedCount)
                        .undeterminedRequirements(result.undeterminedCount)
                        .validatorVersion(VALIDATOR_VERSION)
                        .build());

        // Add detailed lists only if requested
        if (includeDetails) {
            if (!result.unmetRequirements.isEmpty()) {
                responseBuilder.unmetRequirements(result.unmetRequirements);
            }
            if (!result.undeterminedConditions.isEmpty()) {
                responseBuilder.undeterminedConditions(result.undeterminedConditions);
            }
        }

        // Add warnings if any
        if (!result.warnings.isEmpty()) {
            responseBuilder.warnings(result.warnings);
        }

        ValidationResponse response = responseBuilder.build();

        // Set backward compatibility field
        response.setStatusWithBackwardCompatibility(status);

        return response;
    }

    /**
     * Calculate age in years from birth date.
     *
     * @param birthDateStr Birth date in ISO format (YYYY-MM-DD)
     * @return Age in years, or null if unable to calculate
     */
    private Integer calculateAge(String birthDateStr) {
        try {
            LocalDate birthDate = LocalDate.parse(birthDateStr, DATE_FORMATTER);
            LocalDate now = LocalDate.now();
            return Period.between(birthDate, now).getYears();
        } catch (DateTimeParseException e) {
            log.error("Invalid birth date format: {}", birthDateStr);
            return null;
        }
    }

    /**
     * Mask patient ID for privacy in logs.
     * Shows first 4 and last 4 characters, masks the middle.
     *
     * @param patientId Original patient ID
     * @return Masked patient ID (e.g., "pati****t-123")
     */
    private String maskPatientId(String patientId) {
        if (patientId == null || patientId.length() <= 8) {
            return "****";
        }
        int length = patientId.length();
        String start = patientId.substring(0, Math.min(4, length));
        String end = length > 4 ? patientId.substring(length - 4) : "";
        return start + "****" + end;
    }

    /**
     * Normalize vaccine names to match YAML requirements.
     * Database uses uppercase (DTAP, POLIO) but YAML uses mixed case (DTaP, Polio).
     *
     * @param vaccineName Vaccine name from database
     * @return Normalized vaccine name matching YAML format
     */
    private String normalizeVaccineName(String vaccineName) {
        if (vaccineName == null) return null;

        // Map database names to YAML names
        switch (vaccineName.toUpperCase()) {
            case "DTAP":
                return "DTaP";
            case "POLIO":
                return "Polio";
            case "HEPB":
                return "HepB";
            case "HIB":
                return "Hib";
            case "MMR":
                return "MMR";
            case "VARICELLA":
                return "Varicella";
            case "MENACWY":
                return "MenACWY";
            case "TDAP":
                return "Tdap";
            case "MENVEO":
                return "Menveo";
            case "MENACTRA":
                return "Menactra";
            default:
                // Return as-is if no mapping found
                return vaccineName;
        }
    }
}