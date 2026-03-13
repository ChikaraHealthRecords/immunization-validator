package com.immunization.validator;

import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.ValidationResult;
import com.immunization.validator.service.DateConditionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for DateConditionEvaluator - V3 Updated.
 * All assertions now compare against ValidationResult enum.
 */
class DateConditionEvaluatorTest {

    private DateConditionEvaluator evaluator;
    private LocalDate birthDate;

    @BeforeEach
    void setUp() {
        evaluator = new DateConditionEvaluator();
        birthDate = LocalDate.of(2020, 1, 15);
    }

    @Test
    void testFirstDoseAfterFirstBirthday_Met() {
        List<Immunization> imms = List.of(createImmunization("2021-02-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 1st birthday", imms, birthDate));
    }

    @Test
    void testFirstDoseBeforeFirstBirthday_NotMet() {
        List<Immunization> imms = List.of(createImmunization("2020-12-10"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 1st birthday", imms, birthDate));
    }

    @Test
    void testFirstDoseOnFirstBirthday_Met() {
        List<Immunization> imms = List.of(createImmunization("2021-01-15"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 1st birthday", imms, birthDate));
    }

    @Test
    void testFourthDoseAfterFourthBirthday_Met() {
        List<Immunization> imms = List.of(
                createImmunization("2020-03-15"),
                createImmunization("2020-05-15"),
                createImmunization("2020-07-15"),
                createImmunization("2024-02-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("4th dose on or after 4th birthday", imms, birthDate));
    }

    @Test
    void testFourthDoseBeforeFourthBirthday_NotMet() {
        List<Immunization> imms = List.of(
                createImmunization("2020-03-15"),
                createImmunization("2020-05-15"),
                createImmunization("2020-07-15"),
                createImmunization("2023-12-10"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("4th dose on or after 4th birthday", imms, birthDate));
    }

    @Test
    void testNotEnoughDoses_NotMet() {
        List<Immunization> imms = List.of(
                createImmunization("2020-03-15"),
                createImmunization("2020-05-15"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("4th dose on or after 4th birthday", imms, birthDate));
    }

    @Test
    void testTenthBirthdayCondition_Met() {
        List<Immunization> imms = List.of(createImmunization("2030-02-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 10th birthday", imms, birthDate));
    }

    @Test
    void testSixteenthBirthdayCondition_Met() {
        List<Immunization> imms = List.of(createImmunization("2036-03-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 16th birthday", imms, birthDate));
    }

    @Test
    void testEighteenthBirthdayCondition_Met() {
        List<Immunization> imms = List.of(createImmunization("2038-02-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 18th birthday", imms, birthDate));
    }

    @Test
    void testFifteenthMonthCondition_Met() {
        List<Immunization> imms = List.of(createImmunization("2021-04-20"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 15th month", imms, birthDate));
    }

    @Test
    void testFifteenthMonthCondition_NotMet() {
        List<Immunization> imms = List.of(createImmunization("2021-03-10"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("1st dose on or after 15th month", imms, birthDate));
    }

    @Test
    void testMultipleConditions_AllMet() {
        List<Immunization> imms = List.of(
                createImmunization("2021-02-10"),
                createImmunization("2021-04-10"));
        List<String> conditions = List.of(
                "1st dose on or after 1st birthday",
                "2nd dose on or after 1st birthday");
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateConditions(conditions, imms, birthDate));
    }

    @Test
    void testMultipleConditions_OneNotMet() {
        List<Immunization> imms = List.of(
                createImmunization("2021-02-10"),
                createImmunization("2020-12-10"));
        List<String> conditions = List.of(
                "1st dose on or after 1st birthday",
                "2nd dose on or after 1st birthday");
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateConditions(conditions, imms, birthDate));
    }

    @Test
    void testEmptyCondition_Satisfied() {
        List<Immunization> imms = List.of(createImmunization("2020-12-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("", imms, birthDate));
    }

    @Test
    void testNullCondition_Satisfied() {
        List<Immunization> imms = List.of(createImmunization("2020-12-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition(null, imms, birthDate));
    }

    @Test
    void testEmptyConditionsList_Satisfied() {
        List<Immunization> imms = List.of(createImmunization("2020-12-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateConditions(List.of(), imms, birthDate));
    }

    @Test
    void testNullConditionsList_Satisfied() {
        List<Immunization> imms = List.of(createImmunization("2020-12-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateConditions(null, imms, birthDate));
    }

    @Test
    void testThirdDoseAfterFirstBirthday_Met() {
        List<Immunization> imms = List.of(
                createImmunization("2020-03-15"),
                createImmunization("2020-05-15"),
                createImmunization("2021-02-20"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("3rd dose on or after 1st birthday", imms, birthDate));
    }

    @Test
    void testSecondDoseAfterSixteenthBirthday_Met() {
        List<Immunization> imms = List.of(
                createImmunization("2030-02-10"),
                createImmunization("2036-03-10"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("2nd dose on or after 16th birthday", imms, birthDate));
    }

    @Test
    void testNullBirthDate_Undetermined() {
        List<Immunization> imms = List.of(createImmunization("2021-02-10"));
        List<String> conditions = List.of("1st dose on or after 1st birthday");
        assertEquals(ValidationResult.UNDETERMINED,
                evaluator.evaluateConditions(conditions, imms, (LocalDate)null));
    }

    private Immunization createImmunization(String date) {
        return Immunization.builder()
                .vaccineCode("Test")
                .occurrenceDateTime(date)
                .build();
    }
}