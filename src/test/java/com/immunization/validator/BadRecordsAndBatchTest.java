package com.immunization.validator;

import com.immunization.validator.model.ComplianceStatus;
import com.immunization.validator.model.Patient;
import com.immunization.validator.model.StateRequirements;
import com.immunization.validator.model.ValidationRequirement;
import com.immunization.validator.model.ValidationResponse;
import com.immunization.validator.service.DateConditionEvaluator;
import com.immunization.validator.service.IntervalConditionEvaluator;
import com.immunization.validator.service.RequirementsService;
import com.immunization.validator.service.ValidationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 2 of the approved error table: bad records (rows 6 and 7) and batch isolation (row 9).
 * All patient data here is made up. Runs with no profile and no database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DisplayName("Bad records and batch isolation")
class BadRecordsAndBatchTest {

    private static final String SINGLE = "/api/v1/validate/single";
    private static final String BATCH = "/api/v1/validate/batch";

    private static final String UNKNOWN_VACCINE =
            "We couldn't identify one of the vaccines. Please check the vaccine information.";
    private static final String CORRECT_DATE = "Please provide or correct the vaccination date.";
    private static final String SOME_FAILED =
            "Some records could not be processed. Please review the affected records.";

    /** Everything except DTaP that MA requires for K-6, for a child born 2019-01-01. */
    private static final String OTHER_K6_DOSES =
            dose("Polio", "2019-03-01") + "," + dose("Polio", "2019-05-01") + "," + dose("Polio", "2019-07-01") + ","
            + dose("Polio", "2023-02-01") + ","
            + dose("HepB", "2019-01-02") + "," + dose("HepB", "2019-03-01") + "," + dose("HepB", "2019-07-01") + ","
            + dose("MMR", "2020-02-01") + "," + dose("MMR", "2023-02-01") + ","
            + dose("Varicella", "2020-02-01") + "," + dose("Varicella", "2023-02-01");

    /** A complete K-6 record: VALID. */
    private static final String VALID_K6 = dose("DTaP", "2019-03-01") + "," + dose("DTaP", "2019-05-01") + ","
            + dose("DTaP", "2019-07-01") + "," + dose("DTaP", "2020-04-01") + "," + dose("DTaP", "2023-02-01") + ","
            + OTHER_K6_DOSES;

    /** Four DTaP doses with the 4th before the 4th birthday, otherwise complete: INVALID. */
    private static final String INVALID_K6 = dose("DTaP", "2019-03-01") + "," + dose("DTaP", "2019-05-01") + ","
            + dose("DTaP", "2019-07-01") + "," + dose("DTaP", "2022-11-01") + ","
            + OTHER_K6_DOSES;

    @Autowired
    private MockMvc mockMvc;

    private static String dose(String vaccine, String date) {
        return "{\"vaccineCode\":\"" + vaccine + "\",\"occurrenceDateTime\":\"" + date + "\"}";
    }

    private static String patient(String id, String immunizations) {
        return "{\"id\":\"" + id + "\",\"birthDate\":\"2019-01-01\",\"immunization\":[" + immunizations + "]}";
    }

    private ResultActions postSingle(String body, String mode) throws Exception {
        return mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(body)
                .param("state", "MA").param("schoolYear", "K-6").param("responseMode", mode));
    }

    private ResultActions postBatch(String... patients) throws Exception {
        String body = "{\"state\":\"MA\",\"schoolYear\":\"K-6\",\"responseMode\":\"simple\",\"patients\":["
                + String.join(",", patients) + "]}";
        return mockMvc.perform(post(BATCH).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void assertSetAside(ResultActions result, int count) throws Exception {
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("undetermined"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value(
                        "Cannot determine compliance: " + count + " record(s) could not be read"))
                .andExpect(jsonPath("$.warnings.length()").value(count))
                .andExpect(jsonPath("$.errorCode").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    // ─── Row 7: unknown vaccine ───────────────────────────────────────────────

    @Test
    @DisplayName("Row 7: unknown CVX code in simple mode names the record in the warning")
    void row7_unknownCvx() throws Exception {
        String record = VALID_K6 + ",{\"cvxCode\":99999,\"occurrenceDateTime\":\"2020-01-01\"}";
        ResultActions result = postSingle(patient("test-patient-300", record), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(UNKNOWN_VACCINE + " [immunization[16], cvxCode 99999]"))
                .andExpect(jsonPath("$.undeterminedConditions").doesNotExist());
    }

    @Test
    @DisplayName("Row 7: detailed mode adds a condition with the record in its details")
    void row7_unknownCvxDetailed() throws Exception {
        String record = "{\"cvxCode\":99999,\"occurrenceDateTime\":\"2020-01-01\"}";
        postSingle(patient("test-patient-301", record), "detailed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.undeterminedConditions[0].reason").value(UNKNOWN_VACCINE))
                .andExpect(jsonPath("$.undeterminedConditions[0].details").value("immunization[0], cvxCode 99999"));
    }

    @Test
    @DisplayName("Row 7: neither vaccineCode nor cvxCode")
    void row7_noVaccineCode() throws Exception {
        ResultActions result = postSingle(patient("test-patient-302", "{\"occurrenceDateTime\":\"2020-01-01\"}"), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(UNKNOWN_VACCINE + " [immunization[0], no vaccine code]"));
    }

    @Test
    @DisplayName("Row 7: a null element in the immunization list doesn't crash")
    void row7_nullImmunization() throws Exception {
        ResultActions result = postSingle(patient("test-patient-303", dose("DTaP", "2019-03-01") + ",null"), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(UNKNOWN_VACCINE + " [immunization[1], empty record]"));
    }

    // ─── Row 6: dates ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Row 6: an unreadable date names the record and what was supplied")
    void row6_unreadableDate() throws Exception {
        String record = dose("DTaP", "03/01/2019");
        ResultActions result = postSingle(patient("test-patient-304", record), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(
                CORRECT_DATE + " [immunization[0], occurrenceDateTime 03/01/2019]"));
    }

    @Test
    @DisplayName("Row 6: a long unreadable date is cut in the warning")
    void row6_longUnreadableDateIsCut() throws Exception {
        ResultActions result = postSingle(patient("test-patient-305", dose("DTaP", "x".repeat(100))), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(
                CORRECT_DATE + " [immunization[0], occurrenceDateTime " + "x".repeat(40) + "]"));
    }

    @Test
    @DisplayName("Row 6: a missing date on a required vaccine sets the record aside, like an unreadable one")
    void row6_missingDate() throws Exception {
        String records = VALID_K6 + ",{\"vaccineCode\":\"MMR\"}";
        ResultActions result = postSingle(patient("test-patient-306", records), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(
                CORRECT_DATE + " [immunization[16], no occurrenceDateTime]"));
    }

    @Test
    @DisplayName("Row 6: a blank date is treated as missing")
    void row6_blankDate() throws Exception {
        ResultActions result = postSingle(patient("test-patient-307", dose("DTaP", "   ")), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.warnings[0]").value(
                CORRECT_DATE + " [immunization[0], no occurrenceDateTime]"));
    }

    @Test
    @DisplayName("Row 6: two doses of one vaccine, one undated, is never a 500")
    void row6_datedAndUndatedDose() throws Exception {
        String records = dose("DTaP", "2019-03-01") + ",{\"vaccineCode\":\"DTaP\"}";
        assertSetAside(postSingle(patient("test-patient-308", records), "simple"), 1);
    }

    @Test
    @DisplayName("Row 6: several missing dates each get a warning")
    void row6_severalMissingDates() throws Exception {
        String records = "{\"vaccineCode\":\"DTaP\"},{\"vaccineCode\":\"MMR\"}";
        ResultActions result = postSingle(patient("test-patient-309", records), "simple");
        assertSetAside(result, 2);
        result.andExpect(jsonPath("$.warnings[0]").value(CORRECT_DATE + " [immunization[0], no occurrenceDateTime]"))
                .andExpect(jsonPath("$.warnings[1]").value(CORRECT_DATE + " [immunization[1], no occurrenceDateTime]"));
    }

    @Test
    @DisplayName("Row 6: a combination vaccine with a bad date is set aside once, naming its required parts")
    void row6_comboVaccineBadDate() throws Exception {
        // CVX 110: DTaP-HepB-IPV
        postSingle(patient("test-patient-310", "{\"cvxCode\":110,\"occurrenceDateTime\":\"03/01/2019\"}"), "detailed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("undetermined"))
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.undeterminedConditions[0].vaccineCode").value("DTaP, HepB, Polio"));
    }

    // ─── Vaccines that count toward no requirement ───────────────────────────

    @Test
    @DisplayName("Flu and COVID-19 doses don't change the result, even with bad or missing dates")
    void notRequired_badDatesIgnored() throws Exception {
        String records = VALID_K6
                + ",{\"cvxCode\":150,\"occurrenceDateTime\":\"2023-10-01\"}"   // flu, good date
                + ",{\"cvxCode\":150,\"occurrenceDateTime\":\"10/01/2024\"}"   // flu, unreadable date
                + ",{\"cvxCode\":208}"                                         // COVID-19, no date
                + ",{\"vaccineCode\":\"FLU\",\"occurrenceDateTime\":\"   \"}";  // flu by name, blank date
        postSingle(patient("test-patient-311", records), "simple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("valid"))
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }

    @Test
    @DisplayName("CVX 998 (no vaccine administered) is not a dose and doesn't change the result")
    void notRequired_noVaccineAdministered() throws Exception {
        String records = VALID_K6 + ",{\"cvxCode\":998},{\"cvxCode\":998,\"occurrenceDateTime\":\"2020-01-01\"}";
        postSingle(patient("test-patient-312", records), "simple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("valid"))
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }

    // ─── Overall result ───────────────────────────────────────────────────────

    @Test
    @DisplayName("A complete record with one unreadable extra record is UNDETERMINED, not VALID")
    void outcome_validBecomesUndetermined() throws Exception {
        ResultActions result = postSingle(patient("test-patient-310", VALID_K6 + "," + dose("HepB", "bad-date")), "simple");
        assertSetAside(result, 1);
        // The usable records were still evaluated as before.
        result.andExpect(jsonPath("$.metadata.satisfiedRequirements").value(5))
                .andExpect(jsonPath("$.metadata.unsatisfiedRequirements").value(0));
    }

    @Test
    @DisplayName("A failing record with one unreadable record is UNDETERMINED, not INVALID")
    void outcome_invalidBecomesUndetermined() throws Exception {
        ResultActions result = postSingle(patient("test-patient-311", INVALID_K6 + "," + dose("DTaP", "bad-date")), "simple");
        assertSetAside(result, 1);
        result.andExpect(jsonPath("$.metadata.unsatisfiedRequirements").value(1));
    }

    @Test
    @DisplayName("Several set-aside records are counted, with one warning each")
    void outcome_severalSetAside() throws Exception {
        String records = VALID_K6 + "," + dose("HepB", "bad-date") + ",null,{\"cvxCode\":99999,\"occurrenceDateTime\":\"2020-01-01\"}";
        assertSetAside(postSingle(patient("test-patient-312", records), "simple"), 3);
    }

    @Test
    @DisplayName("Regression: a complete record stays VALID with the same counts")
    void regression_valid() throws Exception {
        postSingle(patient("test-patient-313", VALID_K6), "simple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("valid"))
                .andExpect(jsonPath("$.valid").value(true))
                .andExpect(jsonPath("$.message").value("Patient meets all immunization requirements"))
                .andExpect(jsonPath("$.metadata.totalRequirements").value(5))
                .andExpect(jsonPath("$.metadata.satisfiedRequirements").value(5))
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }

    @Test
    @DisplayName("Regression: 4th DTaP before the 4th birthday stays INVALID with the same counts")
    void regression_invalid() throws Exception {
        postSingle(patient("test-patient-314", INVALID_K6), "detailed")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("invalid"))
                .andExpect(jsonPath("$.valid").value(false))
                .andExpect(jsonPath("$.message").value("Patient does not meet requirements: 1 requirement(s) not satisfied"))
                .andExpect(jsonPath("$.metadata.totalRequirements").value(5))
                .andExpect(jsonPath("$.metadata.satisfiedRequirements").value(4))
                .andExpect(jsonPath("$.metadata.unsatisfiedRequirements").value(1))
                .andExpect(jsonPath("$.unmetRequirements[0].vaccineCode").value("DTaP"))
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }

    @Test
    @DisplayName("A known combination CVX code still counts toward every group it covers")
    void regression_comboCvx() throws Exception {
        String records = "{\"cvxCode\":110,\"occurrenceDateTime\":\"2019-03-01\"}";
        postSingle(patient("test-patient-315", records), "simple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("invalid"))
                .andExpect(jsonPath("$.warnings").doesNotExist());
    }

    // ─── Exceptions list ──────────────────────────────────────────────────────

    @Test
    @DisplayName("A null exception or one with no code is ignored with a warning, and the result is unchanged")
    void exceptions_unusableAreIgnored() throws Exception {
        String body = "{\"id\":\"test-patient-316\",\"birthDate\":\"2019-01-01\",\"immunization\":[" + VALID_K6 + "],"
                + "\"exceptions\":[null,{\"exceptionType\":\"RELIGIOUS_EXEMPTION\"}]}";
        postSingle(body, "simple")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("valid"))
                .andExpect(jsonPath("$.warnings.length()").value(2))
                .andExpect(jsonPath("$.warnings[0]").value(
                        "We couldn't read one of the vaccine exceptions, so it was ignored. [exceptions[0]]"))
                .andExpect(jsonPath("$.warnings[1]").value(
                        "We couldn't read one of the vaccine exceptions, so it was ignored. [exceptions[1]]"));
    }

    // ─── No age, school year or birth date ────────────────────────────────────

    @Test
    @DisplayName("No age or school year with age-based rules returns No requirements found, not a crash")
    void noAge_ageBasedRulesDoNotThrow() {
        ValidationRequirement rule = ValidationRequirement.builder().vaccineCode("DTaP").minDoses(1).build();
        StateRequirements rules = StateRequirements.builder().requirementsByAge(Map.of(5, List.of(rule))).build();
        ValidationService service = new ValidationService(new RequirementsService(Map.of("XX", rules)),
                new DateConditionEvaluator(), new IntervalConditionEvaluator(), null);

        Patient noBirthDate = Patient.builder().id("test-patient-317").build();
        Patient badBirthDate = Patient.builder().id("test-patient-318").birthDate("not-a-date").build();

        for (Patient patient : List.of(noBirthDate, badBirthDate)) {
            ValidationResponse response = service.validate(patient, "XX", null, null, false);
            assertThat(response.getStatus()).isEqualTo(ComplianceStatus.UNDETERMINED);
            assertThat(response.getUndeterminedConditions().get(0).getReason()).isEqualTo("No requirements found");
        }
    }

    // ─── Row 9: batch ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("Row 9: [good, unreadable date, blank id, good] returns four results in order")
    void row9_mixedBatch() throws Exception {
        postBatch(patient("batch-good-1", VALID_K6),
                patient("batch-bad-date", VALID_K6 + "," + dose("HepB", "03/01/2019")),
                patient("   ", VALID_K6),
                patient("batch-good-2", INVALID_K6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(SOME_FAILED))
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.results.length()").value(4))

                .andExpect(jsonPath("$.results[0].id").value("batch-good-1"))
                .andExpect(jsonPath("$.results[0].status").value("valid"))
                .andExpect(jsonPath("$.results[0].error").doesNotExist())

                .andExpect(jsonPath("$.results[1].id").value("batch-bad-date"))
                .andExpect(jsonPath("$.results[1].status").value("undetermined"))
                .andExpect(jsonPath("$.results[1].warnings[0]").value(
                        CORRECT_DATE + " [immunization[16], occurrenceDateTime 03/01/2019]"))
                .andExpect(jsonPath("$.results[1].error").doesNotExist())

                .andExpect(jsonPath("$.results[2].id").value("   "))
                .andExpect(jsonPath("$.results[2].status").doesNotExist())
                .andExpect(jsonPath("$.results[2].valid").doesNotExist())
                .andExpect(jsonPath("$.results[2].error.errorCode").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.results[2].error.field").value("id"))
                .andExpect(jsonPath("$.results[2].error.message").value("Patient identifier is required."))

                .andExpect(jsonPath("$.results[3].id").value("batch-good-2"))
                .andExpect(jsonPath("$.results[3].status").value("invalid"));
    }

    @Test
    @DisplayName("Row 9: a batch of good patients has no message or failedCount")
    void row9_allGood() throws Exception {
        postBatch(patient("batch-good-3", VALID_K6), patient("batch-good-4", INVALID_K6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").doesNotExist())
                .andExpect(jsonPath("$.failedCount").doesNotExist())
                .andExpect(jsonPath("$.results[0].status").value("valid"))
                .andExpect(jsonPath("$.results[1].status").value("invalid"));
    }

    @Test
    @DisplayName("Row 9: a batch where every record fails is still a 200")
    void row9_allFail() throws Exception {
        postBatch(patient("", VALID_K6), "null")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").value(2))
                .andExpect(jsonPath("$.message").value(SOME_FAILED))
                .andExpect(jsonPath("$.results.length()").value(2))
                .andExpect(jsonPath("$.results[0].error.field").value("id"))
                .andExpect(jsonPath("$.results[1].error.field").value("id"))
                .andExpect(jsonPath("$.results[1].id").doesNotExist());
    }

    @Test
    @DisplayName("Row 9: a missing date makes only that patient undetermined")
    void row9_missingDateAffectsOnlyThatPatient() throws Exception {
        postBatch(patient("batch-no-date", "{\"vaccineCode\":\"DTaP\"}"), patient("batch-good-5", VALID_K6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").doesNotExist())
                .andExpect(jsonPath("$.results[0].id").value("batch-no-date"))
                .andExpect(jsonPath("$.results[0].status").value("undetermined"))
                .andExpect(jsonPath("$.results[0].warnings[0]").value(
                        CORRECT_DATE + " [immunization[0], no occurrenceDateTime]"))
                .andExpect(jsonPath("$.results[1].status").value("valid"));
    }

    @Test
    @DisplayName("Row 9: a null patient first in the batch doesn't crash")
    void row9_nullFirstPatient() throws Exception {
        postBatch("null", patient("batch-good-6", VALID_K6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedCount").value(1))
                .andExpect(jsonPath("$.results[1].status").value("valid"));
    }

    @Test
    @DisplayName("Row 9: broken characters in an id or date can't fail the whole batch")
    void row9_brokenCharacters() throws Exception {
        postBatch(patient("bad\\ud83d", VALID_K6),
                patient("batch-bad-text", dose("DTaP", "bad\\ud83d")),
                patient("batch-good-7", VALID_K6))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].id").value("bad?"))
                .andExpect(jsonPath("$.results[0].status").value("valid"))
                .andExpect(jsonPath("$.results[1].warnings[0]").value(
                        CORRECT_DATE + " [immunization[0], occurrenceDateTime bad?]"))
                .andExpect(jsonPath("$.results[2].status").value("valid"));
    }

    @Test
    @DisplayName("Row 9: request-level problems are still a 400 for the whole batch")
    void row9_requestLevelProblemsStillFailTheBatch() throws Exception {
        String body = "{\"state\":\"ZZ\",\"schoolYear\":\"K-6\",\"responseMode\":\"simple\",\"patients\":["
                + patient("batch-good-8", VALID_K6) + "]}";
        mockMvc.perform(post(BATCH).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("UNSUPPORTED_STATE"))
                .andExpect(content().string(not(containsString("results"))));
    }
}
