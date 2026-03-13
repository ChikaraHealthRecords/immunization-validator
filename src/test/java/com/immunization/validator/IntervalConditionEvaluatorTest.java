package com.immunization.validator;

import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.ValidationResult;
import com.immunization.validator.service.IntervalConditionEvaluator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for IntervalConditionEvaluator - V3 Updated.
 * All assertions now compare against ValidationResult enum.
 */
class IntervalConditionEvaluatorTest {

    private IntervalConditionEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new IntervalConditionEvaluator();
    }

    @Test
    void test28DaysBetweenDoses_Met() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-01-29"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", imms));
    }

    @Test
    void test28DaysBetweenDoses_NotMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-01-20"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", imms));
    }

    @Test
    void test28DaysExactly_Met() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-01-29"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", imms));
    }

    @Test
    void test6MonthsBetweenLastTwo_Met() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-01"),
                create("2024-03-01"),
                create("2024-09-02"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 6 months between last two doses", imms));
    }

    @Test
    void test6MonthsBetweenLastTwo_NotMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-01"),
                create("2024-03-01"),
                create("2024-07-01"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("at least 6 months between last two doses", imms));
    }

    @Test
    void test8WeeksBetweenDoses_Met() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-27"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 8 weeks between doses", imms));
    }

    @Test
    void test8WeeksBetweenDoses_NotMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-10"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("at least 8 weeks between doses", imms));
    }

    @Test
    void testMultipleDoses_AllPairsMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-01"),
                create("2024-03-05"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", imms));
    }

    @Test
    void testMultipleDoses_OnePairNotMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-01"),
                create("2024-02-15"));
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", imms));
    }

    @Test
    void testOnlyOneDose_Satisfied() {
        List<Immunization> imms = List.of(create("2024-01-01"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", imms));
    }

    @Test
    void testEmptyList_Satisfied() {
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", List.of()));
    }

    @Test
    void testNullList_Satisfied() {
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("at least 28 days between doses", null));
    }

    @Test
    void testEmptyCondition_Satisfied() {
        List<Immunization> imms = List.of(create("2024-01-01"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition("", imms));
    }

    @Test
    void testNullCondition_Satisfied() {
        List<Immunization> imms = List.of(create("2024-01-01"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateCondition(null, imms));
    }

    @Test
    void testMultipleConditions_AllMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-02-01"),
                create("2024-03-05"));
        List<String> conditions = List.of(
                "at least 28 days between doses",
                "at least 4 weeks between doses");
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateConditions(conditions, imms));
    }

    @Test
    void testMultipleConditions_OneNotMet() {
        List<Immunization> imms = List.of(
                create("2024-01-01"),
                create("2024-01-20"));
        List<String> conditions = List.of(
                "at least 28 days between doses",
                "at least 7 days between doses");
        assertEquals(ValidationResult.NOT_SATISFIED,
                evaluator.evaluateConditions(conditions, imms));
    }

    @Test
    void testEmptyConditionsList_Satisfied() {
        List<Immunization> imms = List.of(create("2024-01-01"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateConditions(List.of(), imms));
    }

    @Test
    void testNullConditionsList_Satisfied() {
        List<Immunization> imms = List.of(create("2024-01-01"));
        assertEquals(ValidationResult.SATISFIED,
                evaluator.evaluateConditions(null, imms));
    }

    private Immunization create(String date) {
        return Immunization.builder()
                .vaccineCode("Test")
                .occurrenceDateTime(date)
                .build();
    }
}