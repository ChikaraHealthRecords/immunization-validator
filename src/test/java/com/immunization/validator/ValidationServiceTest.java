package com.immunization.validator;

import com.immunization.validator.model.*;
import com.immunization.validator.service.DateConditionEvaluator;
import com.immunization.validator.service.IntervalConditionEvaluator;
import com.immunization.validator.service.RequirementsService;
import com.immunization.validator.service.ValidationService;
import com.immunization.validator.service.VaccineLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for ValidationService
 * Tests Massachusetts (MA) immunization requirements
 *
 * @author Saakad
 * @since 2026-01-01
 * @version 2.0 - Added VaccineLookupService support for CVX code testing
 */
class ValidationServiceTest {

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

        // Create real instances of evaluators
        dateConditionEvaluator = new DateConditionEvaluator();
        intervalConditionEvaluator = new IntervalConditionEvaluator();

        // Pass all dependencies to ValidationService (including VaccineLookupService)
        validationService = new ValidationService(
                requirementsService,
                dateConditionEvaluator,
                intervalConditionEvaluator,
                vaccineLookupService);
    }

    // ========================================
    // BASIC DOSE COUNTING TESTS (Should Pass)
    // ========================================

    @Test
    @DisplayName("Valid patient with all required doses - should pass")
    void testValidPatient_AllRequiredDoses_ReturnsValid() {
        Patient patient = createPatient("patient-001", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01"),
                createImmunization("DTaP", "2019-05-01"),
                createImmunization("DTaP", "2019-07-01"),
                createImmunization("DTaP", "2020-04-01"),
                createImmunization("DTaP", "2023-02-01")
        ));

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP - 5 doses required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(), "Patient with 5 DTaP doses should be valid");
        assertEquals("patient-001", response.getPatientId());
    }

    @Test
    @DisplayName("Invalid patient with insufficient doses - should fail")
    void testInvalidPatient_InsufficientDoses_ReturnsInvalid() {
        Patient patient = createPatient("patient-002", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01"),
                createImmunization("DTaP", "2019-05-01")
        ));

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP - 5 doses required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid(), "Patient with only 2 DTaP doses should be invalid");
        assertNotNull(response.getUnmetRequirements());
        assertEquals(1, response.getUnmetRequirements().size());

        UnmetRequirement unmet = response.getUnmetRequirements().get(0);
        assertEquals("DTaP", unmet.getVaccineCode());
        assertEquals(5, unmet.getRequiredDoses());
        assertEquals(2, unmet.getFoundDoses());
    }

    // ========================================
    // CRITICAL ISSUE #1: DATE-BASED CONDITIONS
    // ========================================

    @Test
    @DisplayName("CRITICAL TEST: 4th DTaP dose before 4th birthday - should require 5th dose")
    void testCriticalBug_FourthDoseBeforeFourthBirthday_ShouldRequireFifthDose() {
        Patient patient = createPatient("patient-003", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01"),
                createImmunization("DTaP", "2019-05-01"),
                createImmunization("DTaP", "2019-07-01"),
                createImmunization("DTaP", "2022-11-01")
        ));

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("DTaP")
                        .minDoses(5)
                        .description("DTaP - 5 doses required")
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .minDoses(4)
                                        .condition("4th dose on or after 4th birthday")
                                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                                        .description("4 doses acceptable if 4th dose on/after 4th birthday")
                                        .build()
                        ))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid(),
                "Patient with 4th dose before 4th birthday should require 5th dose");
    }

    @Test
    @DisplayName("4th DTaP dose ON 4th birthday - should accept 4 doses")
    void testFourthDoseOnFourthBirthday_ShouldAcceptFourDoses() {
        Patient patient = createPatient("patient-004", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01"),
                createImmunization("DTaP", "2019-05-01"),
                createImmunization("DTaP", "2019-07-01"),
                createImmunization("DTaP", "2023-01-01")
        ));

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("DTaP")
                        .minDoses(5)
                        .description("DTaP - 5 doses required")
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .minDoses(4)
                                        .condition("4th dose on or after 4th birthday")
                                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                                        .description("4 doses acceptable if 4th dose on/after 4th birthday")
                                        .build()
                        ))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "4 doses should be valid when 4th dose on/after 4th birthday");
    }

    @Test
    @DisplayName("4th DTaP dose AFTER 4th birthday - should accept 4 doses")
    void testFourthDoseAfterFourthBirthday_ShouldAcceptFourDoses() {
        Patient patient = createPatient("patient-005", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01"),
                createImmunization("DTaP", "2019-05-01"),
                createImmunization("DTaP", "2019-07-01"),
                createImmunization("DTaP", "2023-06-15")
        ));

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("DTaP")
                        .minDoses(5)
                        .description("DTaP - 5 doses required")
                        .alternateRequirements(List.of(
                                AlternateRequirement.builder()
                                        .minDoses(4)
                                        .condition("4th dose on or after 4th birthday")
                                        .dateConditions(List.of("4th dose on or after 4th birthday"))
                                        .description("4 doses acceptable if 4th dose on/after 4th birthday")
                                        .build()
                        ))
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "4 doses should be valid when 4th dose after 4th birthday");
    }

    // ========================================
    // MEDICAL EXEMPTION TESTS
    // ========================================

    @Test
    @DisplayName("Vaccine exceptions - medical contraindication satisfies requirement")
    void testVaccineException_MedicalContraindication_SatisfiesRequirement() {
        // Create patient WITH exceptions in builder
        Patient patient = Patient.builder()
                .id("patient-011")
                .birthDate("2019-01-01")
                .immunizations(List.of())  // No doses
                .exceptions(List.of(
                        VaccineException.builder()
                                .vaccineCode("DTaP")
                                .exceptionType("MEDICAL_CONTRAINDICATION")
                                .description("Severe allergic reaction to pertussis")
                                .build()
                ))
                .build();

        List<ValidationRequirement> requirements = List.of(
                ValidationRequirement.builder()
                        .vaccineCode("DTaP")
                        .minDoses(5)
                        .acceptedExceptions(List.of("MEDICAL_CONTRAINDICATION"))
                        .description("DTaP required")
                        .build()
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, false);

        assertTrue(response.getValid(),
                "Medical exemption should satisfy requirement even without doses");
    }

    // ========================================
    // EDGE CASES
    // ========================================

    @Test
    @DisplayName("Patient with no immunizations - should fail")
    void testPatientWithNoImmunizations_ReturnsFalse() {
        Patient patient = createPatient("patient-007", "2019-01-01", List.of());

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid());
        assertEquals(1, response.getUnmetRequirements().size());
        assertEquals(0, response.getUnmetRequirements().get(0).getFoundDoses());
    }

    @Test
    @DisplayName("Patient with null immunizations list - should fail gracefully")
    void testPatientWithNullImmunizations_HandlesSafely() {
        Patient patient = createPatient("patient-008", "2019-01-01", null);

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid());
        assertNotNull(response.getUnmetRequirements());
    }

    @Test
    @DisplayName("Invalid birth date format - should handle gracefully")
    void testInvalidBirthDateFormat_HandlesGracefully() {
        Patient patient = createPatient("patient-009", "invalid-date", List.of(
                createImmunization("DTaP", "2019-03-01")
        ));

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        assertDoesNotThrow(() -> {
            ValidationResponse response = validationService.validate(patient, "MA", null, null, false);
            assertNotNull(response);
        });
    }

    @Test
    @DisplayName("Unknown state code - returns empty requirements")
    void testUnknownStateCode_ReturnsInvalid() {
        Patient patient = createPatient("patient-010", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01")
        ));

        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(List.of());

        ValidationResponse response = validationService.validate(patient, "XX", 5, null, true);

        assertFalse(response.getValid());
    }

    @Test
    @DisplayName("Multiple vaccine types - validates each separately")
    void testMultipleVaccineTypes_ValidatesEachSeparately() {
        Patient patient = createPatient("patient-012", "2019-01-01", List.of(
                createImmunization("DTaP", "2019-03-01"),
                createImmunization("DTaP", "2019-05-01"),
                createImmunization("MMR", "2020-01-15")
        ));

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP - 5 doses required"),
                createRequirement("MMR", 2, "MMR - 2 doses required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid());
        assertEquals(2, response.getUnmetRequirements().size());
    }

    @Test
    @DisplayName("All doses on same day - de-duplication collapses to 1 dose")
    void testAllDosesOnSameDay_DeduplicatesToOne() {
        String sameDay = "2024-01-01";
        Patient patient = createPatient("patient-013", "2019-01-01", List.of(
                createImmunization("DTaP", sameDay),
                createImmunization("DTaP", sameDay),
                createImmunization("DTaP", sameDay),
                createImmunization("DTaP", sameDay),
                createImmunization("DTaP", sameDay)
        ));

        List<ValidationRequirement> requirements = List.of(
                createRequirement("DTaP", 5, "DTaP - 5 doses required")
        );
        when(requirementsService.getRequirements(anyString(), any(Integer.class)))
                .thenReturn(requirements);

        ValidationResponse response = validationService.validate(patient, "MA", 5, null, true);

        assertFalse(response.getValid(),
                "5 duplicate doses on same day should de-dup to 1, failing the 5-dose requirement");
        assertEquals(1, response.getUnmetRequirements().get(0).getFoundDoses());
    }

    // ========================================
    // HELPER METHODS
    // ========================================

    private Patient createPatient(String id, String birthDate, List<Immunization> immunizations) {
        return Patient.builder()
                .id(id)
                .birthDate(birthDate)
                .immunizations(immunizations)
                .build();
    }

    private Immunization createImmunization(String vaccineCode, String date) {
        return Immunization.builder()
                .vaccineCode(vaccineCode)
                .occurrenceDateTime(date)
                .build();
    }

    private ValidationRequirement createRequirement(String vaccineCode, int minDoses, String description) {
        return ValidationRequirement.builder()
                .vaccineCode(vaccineCode)
                .minDoses(minDoses)
                .description(description)
                .build();
    }
}