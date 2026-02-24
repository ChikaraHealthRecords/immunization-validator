package com.immunization.validator;

import com.immunization.validator.model.*;
import com.immunization.validator.service.DateConditionEvaluator;
import com.immunization.validator.service.IntervalConditionEvaluator;
import com.immunization.validator.service.RequirementsService;
import com.immunization.validator.service.ValidationService;
import com.immunization.validator.service.VaccineLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Comprehensive test suite for Enhanced YAML features.
 * Tests new Massachusetts DPH requirements including:
 * - Religious exemptions
 * - Heplisav-B (2-dose HepB for 18+)
 * - Complex Polio 3/4/5 dose logic
 * - Birth year exemptions
 * - Signed waivers
 * - Reliable history of chickenpox
 *
 * @author Saakad
 * @since 2026-01-15
 * @version 2.0 - Added VaccineLookupService support for CVX code testing
 */
@DisplayName("Enhanced YAML Features Tests")
class EnhancedYamlFeaturesTest {

    @Mock
    private RequirementsService requirementsService;

    @Mock
    private VaccineLookupService vaccineLookupService;

    private ValidationService validationService;
    private DateConditionEvaluator dateConditionEvaluator;
    private IntervalConditionEvaluator intervalConditionEvaluator;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);

        // ✅ FIX: Create real instances of evaluators (not null!)
        dateConditionEvaluator = new DateConditionEvaluator();
        intervalConditionEvaluator = new IntervalConditionEvaluator();

        // ✅ FIX: Pass real evaluators AND VaccineLookupService to ValidationService
        validationService = new ValidationService(
                requirementsService,
                dateConditionEvaluator,
                intervalConditionEvaluator,
                vaccineLookupService);
    }

    // =========================================================================
    // RELIGIOUS EXEMPTION TESTS
    // =========================================================================

    @Test
    @DisplayName("Religious exemption for DTaP - satisfies requirement")
    void testReligiousExemption_DTaP_SatisfiesRequirement() {
        Patient patient = Patient.builder()
                .id("religious-001")
                .birthDate("2019-01-01")
                .immunizations(List.of())  // No doses
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("DTaP")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Family religious beliefs prohibit vaccination")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("DTaP")
                        .minDoses(5)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION"))
                        .description("DTaP - 5 doses required for K-6")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "Religious exemption should satisfy DTaP requirement");
    }

    @Test
    @DisplayName("Religious exemption for MMR - satisfies requirement")
    void testReligiousExemption_MMR_SatisfiesRequirement() {
        Patient patient = Patient.builder()
                .id("religious-002")
                .birthDate("2019-01-01")
                .immunizations(List.of())
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("MMR")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to MMR vaccine")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("MMR")
                        .minDoses(2)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION", "LABORATORY_EVIDENCE"))
                        .description("MMR - 2 doses required")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "Religious exemption should satisfy MMR requirement");
    }

    // =========================================================================
    // HEPLISAV-B (2-DOSE) TESTS
    // =========================================================================

    @Test
    @DisplayName("Heplisav-B: 2 doses given on/after 18th birthday - should be VALID")
    void testHeplisavB_TwoDoses_OnOrAfter18thBirthday_Valid() {
        Patient patient = Patient.builder()
                .id("heplisav-001")
                .birthDate("2006-09-01")  // Born Sept 1, 2006 → 18th birthday Sept 1, 2024
                .immunizations(List.of(
                        createImmunization("Heplisav-B", "2024-09-15"),  // After 18th birthday
                        createImmunization("Heplisav-B", "2024-10-15")   // 2nd dose
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("HepB")
                        .minDoses(3)
                        .description("Hepatitis B - 3 doses required")
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .alternateVaccineCode("Heplisav-B")
                                        .minDoses(2)
                                        .condition("1st dose on or after 18th birthday")
                                        .description("Heplisav-B 2-dose series acceptable if ≥18 years")
                                        .build()
                        ))
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION", "LABORATORY_EVIDENCE"))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "7-10", false);

        assertTrue(response.getValid(),
                "2 doses of Heplisav-B on/after 18th birthday should satisfy requirement");
    }

    @Test
    @DisplayName("Heplisav-B: 2 doses but 1st dose BEFORE 18th birthday - should be INVALID")
    void testHeplisavB_TwoDoses_Before18thBirthday_Invalid() {
        Patient patient = Patient.builder()
                .id("heplisav-002")
                .birthDate("2006-09-01")
                .immunizations(List.of(
                        createImmunization("Heplisav-B", "2024-08-15"),  // Before 18th birthday
                        createImmunization("Heplisav-B", "2024-10-15")
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("HepB")
                        .minDoses(3)
                        .description("Hepatitis B - 3 doses required")
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .alternateVaccineCode("Heplisav-B")
                                        .minDoses(2)
                                        .condition("1st dose on or after 18th birthday")
                                        .description("Heplisav-B acceptable if ≥18 years")
                                        .build()
                        ))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "7-10", true);

        assertFalse(response.getValid(),
                "Heplisav-B before 18th birthday should not satisfy alternate requirement");
    }

    // =========================================================================
    // COMPLEX POLIO 3/4/5 DOSE LOGIC TESTS
    // =========================================================================

    @Test
    @DisplayName("Polio: 4 doses with 4th on/after 4th birthday and ≥6 months - VALID")
    void testPolio_FourDoses_MeetsBothConditions_Valid() {
        Patient patient = Patient.builder()
                .id("polio-001")
                .birthDate("2019-01-01")
                .immunizations(List.of(
                        createImmunization("Polio", "2019-03-01"),
                        createImmunization("Polio", "2019-05-01"),
                        createImmunization("Polio", "2019-07-01"),
                        createImmunization("Polio", "2023-02-01")  // On/after 4th birthday + 6 months after 3rd
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("Polio")
                        .minDoses(4)
                        .description("Polio - 4 doses required with timing")
                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                        .intervalConditions(List.of("at least 6 months between last two doses"))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "4 Polio doses meeting both date and interval conditions should be valid");
    }

    @Test
    @DisplayName("Polio: 3 doses with 3rd on/after 4th birthday and ≥6 months - VALID alternate")
    void testPolio_ThreeDoses_AlternateSchedule_Valid() {
        Patient patient = Patient.builder()
                .id("polio-002")
                .birthDate("2019-01-01")
                .immunizations(List.of(
                        createImmunization("Polio", "2019-03-01"),
                        createImmunization("Polio", "2019-05-01"),
                        createImmunization("Polio", "2023-02-01")  // 3rd dose on/after 4th birthday + 6 months after 2nd
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("Polio")
                        .minDoses(4)
                        .description("Polio - 4 doses required")
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .minDoses(3)
                                        .condition("3rd dose on or after 4th birthday")
                                        .description("3 doses acceptable if 3rd dose on/after 4th birthday AND ≥6 months after previous")
                                        .dateConditions(List.of("3rd dose on or after 4th birthday"))
                                        .intervalConditions(List.of("at least 6 months between last two doses"))
                                        .build()
                        ))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "3 Polio doses with 3rd on/after 4th birthday should satisfy alternate requirement");
    }

    @Test
    @DisplayName("Polio: 4 doses but 4th BEFORE 4th birthday - requires 5th dose")
    void testPolio_FourDoses_FourthBeforeFourthBirthday_NeedsFifth() {
        Patient patient = Patient.builder()
                .id("polio-003")
                .birthDate("2019-01-01")
                .immunizations(List.of(
                        createImmunization("Polio", "2019-03-01"),
                        createImmunization("Polio", "2019-05-01"),
                        createImmunization("Polio", "2019-07-01"),
                        createImmunization("Polio", "2022-11-01")  // Before 4th birthday
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("Polio")
                        .minDoses(4)
                        .description("Polio - 4 doses required")
                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .minDoses(5)
                                        .description("5 doses required if 4th dose fails timing")
                                        .build()
                        ))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid(),
                "4 Polio doses with 4th before 4th birthday should require 5th dose");
    }

    // =========================================================================
    // BIRTH YEAR EXEMPTION TESTS
    // =========================================================================

    @Test
    @DisplayName("College MMR: Birth before 1957 - satisfies requirement")
    void testCollegeMMR_BirthBefore1957_SatisfiesRequirement() {
        Patient patient = Patient.builder()
                .id("birth-1956")
                .birthDate("1956-06-15")
                .immunizations(List.of())  // No MMR doses
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("MMR")
                                .exceptionType("BIRTH_BEFORE_1957")
                                .description("Born in U.S. before 1957")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("MMR")
                        .minDoses(2)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION",
                                "LABORATORY_EVIDENCE", "BIRTH_BEFORE_1957"))
                        .description("MMR - 2 doses required for college")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "college", false);

        assertTrue(response.getValid(),
                "Birth before 1957 should satisfy MMR requirement for non-health science students");
    }

    @Test
    @DisplayName("College Varicella: Birth before 1980 - satisfies requirement")
    void testCollegeVaricella_BirthBefore1980_SatisfiesRequirement() {
        Patient patient = Patient.builder()
                .id("birth-1979")
                .birthDate("1979-03-20")
                .immunizations(List.of())
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("Varicella")
                                .exceptionType("BIRTH_BEFORE_1980")
                                .description("Born in U.S. before 1980")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("Varicella")
                        .minDoses(2)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION",
                                "LABORATORY_EVIDENCE", "RELIABLE_HISTORY_CHICKENPOX", "BIRTH_BEFORE_1980"))
                        .description("Varicella - 2 doses required for college")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "college", false);

        assertTrue(response.getValid(),
                "Birth before 1980 should satisfy Varicella requirement for non-health science students");
    }

    // =========================================================================
    // RELIABLE HISTORY OF CHICKENPOX TESTS
    // =========================================================================

    @Test
    @DisplayName("Varicella: Reliable history of chickenpox - satisfies requirement")
    void testVaricella_ReliableHistoryChickenpox_SatisfiesRequirement() {
        Patient patient = Patient.builder()
                .id("chickenpox-001")
                .birthDate("2015-01-01")
                .immunizations(List.of())  // No Varicella doses
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("Varicella")
                                .exceptionType("RELIABLE_HISTORY_CHICKENPOX")
                                .description("Physician-verified history of chickenpox disease")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("Varicella")
                        .minDoses(2)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION",
                                "LABORATORY_EVIDENCE", "RELIABLE_HISTORY_CHICKENPOX"))
                        .description("Varicella - 2 doses required")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "Reliable history of chickenpox should satisfy Varicella requirement");
    }

    // =========================================================================
    // SIGNED WAIVER TESTS
    // =========================================================================

    @Test
    @DisplayName("College MenACWY: Signed waiver - satisfies requirement")
    void testCollegeMenACWY_SignedWaiver_SatisfiesRequirement() {
        Patient patient = Patient.builder()
                .id("waiver-001")
                .birthDate("2005-01-01")
                .immunizations(List.of())  // No MenACWY doses
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("MenACWY")
                                .exceptionType("SIGNED_WAIVER")
                                .description("Student declined after reading MDPH waiver")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("MenACWY")
                        .minDoses(1)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION", "SIGNED_WAIVER"))
                        .description("MenACWY - 1 dose required for college students ≤21")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "college", false);

        assertTrue(response.getValid(),
                "Signed waiver should satisfy MenACWY requirement for college");
    }

    // =========================================================================
    // COMBINATION TESTS (Multiple New Features)
    // =========================================================================

    @Test
    @DisplayName("Preschool: Multiple vaccines with religious exemptions")
    void testPreschool_MultipleReligiousExemptions() {
        Patient patient = Patient.builder()
                .id("religious-multi-001")
                .birthDate("2020-01-01")
                .immunizations(List.of())  // No vaccines
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("DTaP")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to all vaccines")
                                .build(),
                        VaccineException.builder()
                                .vaccineCode("MMR")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to all vaccines")
                                .build(),
                        VaccineException.builder()
                                .vaccineCode("Varicella")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to all vaccines")
                                .build(),
                        VaccineException.builder()
                                .vaccineCode("HepB")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to all vaccines")
                                .build(),
                        VaccineException.builder()
                                .vaccineCode("Polio")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to all vaccines")
                                .build(),
                        VaccineException.builder()
                                .vaccineCode("Hib")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to all vaccines")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 4),
                createRequirement("MMR", 1),
                createRequirement("Varicella", 1),
                createRequirement("HepB", 3),
                createRequirement("Polio", 3),
                createRequirement("Hib", 1)
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "preschool", false);

        assertTrue(response.getValid(),
                "Religious exemptions for all vaccines should satisfy all preschool requirements");
    }

    @Test
    @DisplayName("Grade 7-10: Combination of doses and exemptions")
    void testGrade7_CombinationDosesAndExemptions() {
        Patient patient = Patient.builder()
                .id("combo-001")
                .birthDate("2010-01-01")
                .immunizations(List.of(
                        createImmunization("Tdap", "2022-06-01"),
                        createImmunization("HepB", "2010-03-01"),
                        createImmunization("HepB", "2010-05-01")
                        // Missing: HepB 3rd dose, but has religious exemption
                ))
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("HepB")
                                .exceptionType("RELIGIOUS_EXEMPTION")
                                .description("Religious objection to 3rd HepB dose")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("Tdap")
                        .minDoses(1)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION"))
                        .build(),
                ValidationRequirement.builder()
                        .vaccineCode("HepB")
                        .minDoses(3)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION", "LABORATORY_EVIDENCE"))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), anyString()))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", null, "7-10", false);

        assertTrue(response.getValid(),
                "Combination of actual doses and religious exemptions should satisfy requirements");
    }

    // =========================================================================
    // HELPER METHODS
    // =========================================================================

    private Immunization createImmunization(String vaccineCode, String date) {
        return Immunization.builder()
                .vaccineCode(vaccineCode)
                .occurrenceDateTime(date)
                .build();
    }

    private ValidationRequirement createRequirement(String vaccineCode, int minDoses) {
        return ValidationRequirement.builder()
                .vaccineCode(vaccineCode)
                .minDoses(minDoses)
                .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION", "RELIGIOUS_EXEMPTION", "LABORATORY_EVIDENCE"))
                .description(vaccineCode + " - " + minDoses + " doses required")
                .build();
    }
}