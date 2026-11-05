package com.immunization.validator;

import com.immunization.validator.config.RequirementsConfiguration;
import com.immunization.validator.config.StateRulesLoader;
import com.immunization.validator.model.*;
import com.immunization.validator.service.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Uses the real vaccine list (vaccines/vaccines.csv) and the real
 * Massachusetts rules (rules/states/ma.yml), because the bugs these cover
 * come from how the two fit together, not from either one alone.
 */
class CvxGroupRulesTest {

    private ValidationService validationService;

    @BeforeEach
    void setUp() {
        VaccineCatalog catalog = new FileVaccineCatalog(new ClassPathResource("vaccines/vaccines.csv"));
        Map<String, StateRequirements> rules = Map.of("MA",
                RequirementsConfiguration.toStateRequirements(
                        new StateRulesLoader().load(StateRulesLoader.DEFAULT_LOCATION).get("MA")));
        validationService = new ValidationService(
                new RequirementsService(rules),
                new DateConditionEvaluator(),
                new IntervalConditionEvaluator(),
                new VaccineLookupService(catalog));
    }

    private ValidationResponse validate(Patient patient, String schoolYear) {
        return validationService.validate(patient, "MA", null, schoolYear, true);
    }

    private static Immunization cvx(int code, String date) {
        Immunization imm = new Immunization();
        imm.setCvxCode(code);
        imm.setOccurrenceDateTime(date);
        return imm;
    }

    private static Immunization named(String vaccineCode, String date) {
        Immunization imm = new Immunization();
        imm.setVaccineCode(vaccineCode);
        imm.setOccurrenceDateTime(date);
        return imm;
    }

    private static Patient patient(String birthDate, List<Immunization> doses) {
        Patient p = new Patient();
        p.setId("made-up-0001");
        p.setBirthDate(birthDate);
        p.setImmunizations(new ArrayList<>(doses));
        return p;
    }

    private static Optional<UnmetRequirement> unmet(ValidationResponse r, String code) {
        return Optional.ofNullable(r.getUnmetRequirements()).orElse(List.of()).stream()
                .filter(u -> code.equals(u.getVaccineCode())).findFirst();
    }

    private static Optional<UndeterminedCondition> needsReview(ValidationResponse r, String code) {
        return Optional.ofNullable(r.getUndeterminedConditions()).orElse(List.of()).stream()
                .filter(u -> code.equals(u.getVaccineCode())).findFirst();
    }

    private static List<String> warnings(ValidationResponse r) {
        return Optional.ofNullable(r.getWarnings()).orElse(List.of());
    }

    // ─── MenACWY recorded by CVX code ────────────────────────────────────────

    @Nested
    @DisplayName("MenACWY recorded by CVX code (7th grade, born 2013-05-01)")
    class MenACWY {

        private ValidationResponse seventhGrader(int menCvx) {
            return validate(patient("2013-05-01", List.of(
                    cvx(115, "2024-06-01"),          // Tdap
                    cvx(menCvx, "2024-06-01"))), "7-10");
        }

        @Test
        @DisplayName("Menactra, Menveo, MenQuadfi and MenABCWY all count")
        void conjugateCodesCount() {
            for (int code : List.of(108, 114, 136, 147, 203, 316, 328)) {
                ValidationResponse r = seventhGrader(code);
                assertTrue(unmet(r, "MenACWY").isEmpty(), "CVX " + code + " should meet MenACWY");
                assertTrue(unmet(r, "Tdap").isEmpty(), "Tdap (CVX 115) should be met");
            }
        }

        @Test
        @DisplayName("MPSV4, MenC-only and Hib-MenCY don't count as MenACWY")
        void otherMeningococcalCodesDoNot() {
            for (int code : List.of(32, 103, 148)) {
                assertTrue(unmet(seventhGrader(code), "MenACWY").isPresent(), "CVX " + code + " must not meet MenACWY");
            }
        }

        @Test
        @DisplayName("MenB alone doesn't count as MenACWY")
        void menBDoesNot() {
            assertTrue(unmet(seventhGrader(162), "MenACWY").isPresent());
        }

        @Test
        @DisplayName("By name it still works as before")
        void byName() {
            ValidationResponse r = validate(patient("2013-05-01", List.of(
                    named("Tdap", "2024-06-01"), named("MenACWY", "2024-06-01"))), "7-10");
            assertTrue(unmet(r, "MenACWY").isEmpty());
        }
    }

    // ─── DT counted toward DTaP ──────────────────────────────────────────────

    @Nested
    @DisplayName("Pediatric DT (CVX 28) toward DTaP (K-6, born 2019-03-14)")
    class DtForDtap {

        /** Everything K-6 needs except DTaP, so DTaP decides the overall result. */
        private List<Immunization> otherK6Doses() {
            return List.of(
                    named("Polio", "2019-05-14"), named("Polio", "2019-07-15"),
                    named("Polio", "2020-03-16"), named("Polio", "2023-04-10"),
                    named("HepB", "2019-03-15"), named("HepB", "2019-04-15"), named("HepB", "2019-09-16"),
                    named("MMR", "2020-03-16"), named("MMR", "2023-04-10"),
                    named("Varicella", "2020-03-16"), named("Varicella", "2023-04-10"));
        }

        private Patient k6(List<Immunization> dtapDoses) {
            List<Immunization> all = new ArrayList<>(otherK6Doses());
            all.addAll(dtapDoses);
            return patient("2019-03-14", all);
        }

        // Four DTaP doses, the 4th before the 4th birthday, so the 4-dose route doesn't apply.
        private final List<Immunization> fourEarlyDtap = List.of(
                named("DTaP", "2019-05-14"), named("DTaP", "2019-07-15"),
                named("DTaP", "2019-09-16"), named("DTaP", "2020-06-15"));

        @Test
        @DisplayName("Met only by counting DT: needs review, with an explanation")
        void metOnlyWithDt() {
            List<Immunization> doses = new ArrayList<>(fourEarlyDtap);
            doses.add(cvx(28, "2023-04-10"));
            ValidationResponse r = validate(k6(doses), "K-6");

            assertEquals(ComplianceStatus.UNDETERMINED, r.getStatus());
            UndeterminedCondition c = needsReview(r, "DTaP").orElseThrow();
            assertEquals("DT given instead of DTaP", c.getReason());
            assertTrue(c.getDetails().contains("pertussis"), c.getDetails());
            assertTrue(c.getDetails().contains("4 of 5"), c.getDetails());
            assertTrue(c.getSuggestion().contains("medical contraindication"), c.getSuggestion());
            assertTrue(unmet(r, "DTaP").isEmpty());
        }

        @Test
        @DisplayName("Met without the DT dose: valid, DT ignored")
        void metWithoutDt() {
            List<Immunization> doses = new ArrayList<>(fourEarlyDtap);
            doses.add(named("DTaP", "2023-04-10"));
            doses.add(cvx(28, "2021-01-05"));
            ValidationResponse r = validate(k6(doses), "K-6");

            assertEquals(ComplianceStatus.VALID, r.getStatus());
            assertTrue(needsReview(r, "DTaP").isEmpty());
        }

        @Test
        @DisplayName("Not met even counting DT: missing, plus a warning about the DT dose")
        void notMetEvenWithDt() {
            ValidationResponse r = validate(k6(List.of(
                    named("DTaP", "2019-05-14"), named("DTaP", "2019-07-15"), cvx(28, "2019-09-16"))), "K-6");

            assertEquals(ComplianceStatus.INVALID, r.getStatus());
            assertTrue(unmet(r, "DTaP").isPresent());
            assertTrue(warnings(r).stream().anyMatch(w -> w.contains("pediatric DT")), warnings(r).toString());
        }

        @Test
        @DisplayName("With a medical contraindication on file: valid")
        void withMedicalException() {
            List<Immunization> doses = new ArrayList<>(fourEarlyDtap);
            doses.add(cvx(28, "2023-04-10"));
            Patient p = k6(doses);
            VaccineException exception = new VaccineException();
            exception.setVaccineCode("DTaP");
            exception.setExceptionType("MEDICAL_CONTRAINDICATION");
            p.setExceptions(List.of(exception));

            assertEquals(ComplianceStatus.VALID, validate(p, "K-6").getStatus());
        }

        @Test
        @DisplayName("A complete DTaP series is unaffected")
        void noDt() {
            List<Immunization> doses = new ArrayList<>(fourEarlyDtap);
            doses.add(named("DTaP", "2023-04-10"));
            assertEquals(ComplianceStatus.VALID, validate(k6(doses), "K-6").getStatus());
        }
    }

    // ─── CVX codes missing from the vaccine list ─────────────────────────────

    @Test
    @DisplayName("An unknown CVX code is skipped with a warning instead of failing the whole record")
    void unknownCvxCode() {
        ValidationResponse r = validate(patient("2019-03-14", List.of(
                cvx(337, "2025-10-01"),               // a newer flu code not in our list
                named("MMR", "2020-03-16"),
                named("MMR", "2023-04-10"))), "K-6");

        assertNotNull(r.getStatus());
        assertTrue(warnings(r).stream().anyMatch(w -> w.contains("CVX code 337")), warnings(r).toString());
        assertTrue(unmet(r, "MMR").isEmpty(), "the rest of the record is still checked");
    }
}
