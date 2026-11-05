package com.immunization.validator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 1 of the approved error table: request-level errors, the error contract and request ids.
 * All patient data here is made up. Runs with no profile and no database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("API error contract")
class ApiErrorContractTest {

    private static final String SINGLE = "/api/v1/validate/single";
    private static final String BATCH = "/api/v1/validate/batch";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private static final String PATIENT = "{\"id\":\"test-patient-100\",\"birthDate\":\"2019-01-01\",\"immunization\":[]}";

    @Autowired
    private MockMvc mockMvc;

    /** What every error must satisfy, whichever scenario produced it. */
    private ResultActions assertError(ResultActions result, int http, String errorCode, String field, String message)
            throws Exception {
        result.andExpect(status().is(http))
                .andExpect(jsonPath("$.httpStatus").value(http))
                .andExpect(jsonPath("$.errorCode").value(errorCode))
                .andExpect(jsonPath("$.message").value(message))
                .andExpect(jsonPath("$.requestId").isNotEmpty())
                .andExpect(header().exists("X-Request-Id"))
                // A clinical outcome never appears on an error.
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.valid").doesNotExist())
                // No stack trace or exception text.
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("at com."))))
                .andExpect(content().string(not(containsString("java."))));
        if (field != null) {
            result.andExpect(jsonPath("$.field").value(field));
        } else {
            result.andExpect(jsonPath("$.field").doesNotExist());
        }
        return result;
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions postPatient(String stateParam) throws Exception {
        return mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                .param("state", stateParam).param("schoolYear", "K-6"));
    }

    // ─── Row 1: missing state ─────────────────────────────────────────────────

    @Test
    @DisplayName("Row 1: /single with no state")
    void row1_missingState_single() throws Exception {
        assertError(postJson(SINGLE, PATIENT), 400, "MISSING_PARAMETER", "state", "Please select a state.");
    }

    @Test
    @DisplayName("Row 1: /single with an empty state")
    void row1_blankState_single() throws Exception {
        assertError(postPatient(""), 400, "MISSING_PARAMETER", "state", "Please select a state.");
        assertError(postPatient("   "), 400, "MISSING_PARAMETER", "state", "Please select a state.");
    }

    @Test
    @DisplayName("Row 1: /batch with no state")
    void row1_missingState_batch() throws Exception {
        assertError(postJson(BATCH, "{\"responseMode\":\"simple\",\"patients\":[" + PATIENT + "]}"),
                400, "VALIDATION_FAILED", "state", "Please select a state.");
    }

    // ─── Row 2: missing patient id ────────────────────────────────────────────

    @Test
    @DisplayName("Row 2: /single with no patient id")
    void row2_missingPatientId() throws Exception {
        String noId = "{\"birthDate\":\"2019-01-01\",\"immunization\":[]}";
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(noId)
                        .param("state", "MA").param("schoolYear", "K-6")),
                400, "VALIDATION_FAILED", "id", "Patient identifier is required.")
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    // ─── Row 3: invalid age ───────────────────────────────────────────────────

    @Test
    @DisplayName("Row 3: age=five on /single")
    void row3_invalidAge_single() throws Exception {
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                        .param("state", "MA").param("age", "five")),
                400, "INVALID_PARAMETER_TYPE", "age", "Age must be a whole number.")
                .andExpect(jsonPath("$.expectedType").value("integer"))
                .andExpect(jsonPath("$.rejectedValue").doesNotExist());
    }

    @Test
    @DisplayName("Row 3: \"age\":\"five\" in a /batch body")
    void row3_invalidAge_batch() throws Exception {
        assertError(postJson(BATCH, "{\"state\":\"MA\",\"age\":\"five\",\"responseMode\":\"simple\",\"patients\":[" + PATIENT + "]}"),
                400, "INVALID_PARAMETER_TYPE", "age", "Age must be a whole number.")
                .andExpect(jsonPath("$.expectedType").value("integer"));
    }

    // ─── Row 4: malformed JSON ────────────────────────────────────────────────

    private static final String UNREADABLE = "We couldn't read the submitted record. Please check its format.";

    @Test
    @DisplayName("Row 4: malformed JSON")
    void row4_malformedJson() throws Exception {
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content("{invalid-json")
                        .param("state", "MA")),
                400, "MALFORMED_REQUEST", null, UNREADABLE)
                .andExpect(jsonPath("$.details.length()").value(1))
                .andExpect(jsonPath("$.details[0].errorCode").value("MALFORMED_REQUEST"))
                .andExpect(jsonPath("$.details[0].field").doesNotExist())
                .andExpect(jsonPath("$.details[0].message",
                        matchesPattern("The record is not valid JSON \\(line 1, column \\d+\\)\\.")));
    }

    @Test
    @DisplayName("Row 4: the explanation gives the line the parser stopped on")
    void row4_explanationGivesLine() throws Exception {
        assertError(postJson(SINGLE + "?state=MA", "{\n\"id\":\"test-patient-100\",\n\"birthDate\" oops\n}"),
                400, "MALFORMED_REQUEST", null, UNREADABLE)
                .andExpect(jsonPath("$.details[0].message",
                        matchesPattern("The record is not valid JSON \\(line 3, column \\d+\\)\\.")));
    }

    @Test
    @DisplayName("Row 4: a syntax error inside the immunization list is still reported as invalid JSON")
    void row4_syntaxErrorInsideList() throws Exception {
        assertError(postJson(SINGLE + "?state=MA", "{\"id\":\"p\",\"immunization\":[{\"vaccineCode\":}]}"),
                400, "MALFORMED_REQUEST", null, UNREADABLE)
                .andExpect(jsonPath("$.details[0].message",
                        matchesPattern("The record is not valid JSON \\(line 1, column \\d+\\)\\.")));
    }

    @Test
    @DisplayName("Row 4: valid JSON with a wrong value says so, without quoting the value")
    void row4_validJsonWrongShape() throws Exception {
        assertError(postJson(SINGLE + "?state=MA",
                        "{\"id\":\"p\",\"immunization\":[{\"cvxCode\":\"MADE-UP-MARKER\"}]}"),
                400, "MALFORMED_REQUEST", null, UNREADABLE)
                .andExpect(jsonPath("$.details[0].message",
                        matchesPattern("The record is not in the expected format( \\(line 1, column \\d+\\))?\\.")))
                .andExpect(content().string(not(containsString("MADE-UP-MARKER"))));
    }

    @Test
    @DisplayName("Row 4: no body at all")
    void row4_missingBody() throws Exception {
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).param("state", "MA")),
                400, "MALFORMED_REQUEST", null, UNREADABLE)
                .andExpect(jsonPath("$.details[0].message").value("No record was submitted."));
    }

    @Test
    @DisplayName("Row 4: the submitted content is in neither the log nor the response")
    void row4_contentNotExposed(CapturedOutput output) throws Exception {
        assertError(postJson(SINGLE + "?state=MA", "{\"id\":\"MADE-UP-LOG-MARKER\", broken"),
                400, "MALFORMED_REQUEST", null, UNREADABLE)
                .andExpect(content().string(not(containsString("MADE-UP-LOG-MARKER"))))
                .andExpect(content().string(not(containsString("broken"))))
                .andExpect(content().string(not(containsString("JsonParse"))));
        assertThat(output.getAll()).doesNotContain("MADE-UP-LOG-MARKER");
    }

    // ─── Row 5: unsupported Content-Type ──────────────────────────────────────

    @Test
    @DisplayName("Row 5: text/plain body")
    void row5_unsupportedContentType() throws Exception {
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.TEXT_PLAIN).content(PATIENT)
                        .param("state", "MA")),
                415, "UNSUPPORTED_MEDIA_TYPE", null, "Please submit your record in JSON format.");
    }

    // ─── Row 8: unsupported state ─────────────────────────────────────────────

    private static final String STATE_NOT_SUPPORTED = "Validation is not currently supported for the selected state.";

    @Test
    @DisplayName("Row 8: /single with an unsupported state")
    void row8_unsupportedState_single() throws Exception {
        assertError(postPatient("ZZ"), 400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("ZZ"))
                .andExpect(jsonPath("$.expectedType").doesNotExist());
    }

    @Test
    @DisplayName("Row 8: rejectedValue keeps at most 10 characters")
    void row8_rejectedValueIsShortened() throws Exception {
        assertError(postPatient("ABCDEFGHIJKLMNOP"), 400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("ABCDEFGHIJ"));
        assertError(postPatient("ABCDEFGHIJ"), 400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("ABCDEFGHIJ"));
    }

    @Test
    @DisplayName("Row 8: shortening never cuts an emoji in half")
    void row8_shorteningKeepsEmojiWhole() throws Exception {
        String emoji = "😀";
        assertError(postPatient("ABCDEFGHI" + emoji + "KLM"), 400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("ABCDEFGHI" + emoji));
    }

    @Test
    @DisplayName("Row 8: a broken character in a /batch state is still a 400, not a 500")
    void row8_brokenCharacterIsStillA400() throws Exception {
        assertError(postJson(BATCH, "{\"state\":\"Z\\ud83d\",\"responseMode\":\"simple\",\"patients\":[" + PATIENT + "]}"),
                400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("Z?"));
    }

    @Test
    @DisplayName("Row 8: state matching ignores case and surrounding spaces")
    void row8_stateIgnoresCase() throws Exception {
        postPatient("ma").andExpect(status().isOk())
                .andExpect(jsonPath("$.metadata.state").value("MA"));
        postPatient(" Ma ").andExpect(status().isOk());
    }

    @Test
    @DisplayName("Row 8: /batch state matching ignores case")
    void row8_stateIgnoresCase_batch() throws Exception {
        postJson(BATCH, "{\"state\":\"ma\",\"schoolYear\":\"K-6\",\"responseMode\":\"simple\",\"patients\":["
                + PATIENT + "]}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.results[0].metadata.state").value("MA"));
    }

    @Test
    @DisplayName("Row 8: /batch with an unsupported state fails the whole request")
    void row8_unsupportedState_batch() throws Exception {
        assertError(postJson(BATCH, "{\"state\":\"ZZ\",\"responseMode\":\"simple\",\"patients\":[" + PATIENT + "]}"),
                400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("ZZ"))
                .andExpect(jsonPath("$.results").doesNotExist());
    }

    @Test
    @DisplayName("Row 8: a rejected state cannot forge a log line, and is JSON-encoded and shortened in the body")
    void row8_rejectedStateIsSafe(CapturedOutput output) throws Exception {
        String attack = "ZZ\r\n2026-01-01 FAKE-LOG-ENTRY";
        assertError(postPatient(attack), 400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED)
                .andExpect(jsonPath("$.rejectedValue").value("ZZ\r\n2026-0"))
                .andExpect(content().string(containsString("\"rejectedValue\":\"ZZ\\r\\n2026-0\"")))
                .andExpect(content().string(not(containsString("FAKE-LOG-ENTRY"))));
        assertThat(output.getAll())
                .contains("Unsupported state rejected: ZZ__2026-01-01_FAKE-LOG-ENTRY")
                .doesNotContain("\n2026-01-01 FAKE-LOG-ENTRY");
    }

    @Test
    @DisplayName("Row 8: a supported state with no matching rules keeps today's 200 UNDETERMINED")
    void row8_supportedStateWithoutRulesIsStillUndetermined() throws Exception {
        mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                        .param("state", "MA").param("schoolYear", "no-such-year"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("undetermined"))
                .andExpect(jsonPath("$.undeterminedConditions[0].reason").value("No requirements found"))
                .andExpect(jsonPath("$.errorCode").doesNotExist());
    }

    // ─── Other parts of the contract ──────────────────────────────────────────

    @Test
    @DisplayName("Several failing fields: details lists them all, field is the first")
    void contract_multipleFieldsFillDetails() throws Exception {
        assertError(postJson(BATCH, "{}"), 400, "VALIDATION_FAILED", "patients", "At least one patient record is required.")
                .andExpect(jsonPath("$.details.length()").value(3))
                .andExpect(jsonPath("$.details[0].field").value("patients"))
                .andExpect(jsonPath("$.details[1].field").value("responseMode"))
                .andExpect(jsonPath("$.details[1].message").value("Response mode is required."))
                .andExpect(jsonPath("$.details[2].field").value("state"))
                .andExpect(jsonPath("$.details[2].errorCode").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("/batch with an empty patient list is a 400")
    void contract_emptyPatients() throws Exception {
        assertError(postJson(BATCH, "{\"state\":\"MA\",\"responseMode\":\"simple\",\"patients\":[]}"),
                400, "VALIDATION_FAILED", "patients", "At least one patient record is required.");
    }

    @Test
    @DisplayName("Wrong HTTP method: 405")
    void contract_methodNotAllowed() throws Exception {
        assertError(mockMvc.perform(get(SINGLE)), 405, "METHOD_NOT_ALLOWED", null,
                "This action isn't available at this address.")
                .andExpect(header().string("Allow", "POST"));
    }

    @Test
    @DisplayName("Unknown path: 404")
    void contract_notFound() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/no-such-path")), 404, "NOT_FOUND", null,
                "We couldn't find what you were looking for. Please check the address.");
    }

    @Test
    @DisplayName("Accept header ruling out JSON: a rejected request still gets a JSON error")
    void contract_errorIsJsonWhateverTheAcceptHeader() throws Exception {
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                        .accept(MediaType.TEXT_PLAIN).param("state", "ZZ")),
                400, "UNSUPPORTED_STATE", "state", STATE_NOT_SUPPORTED);
    }

    @Test
    @DisplayName("Accept header ruling out JSON: a good request gets a 406 in the contract shape")
    void contract_notAcceptable() throws Exception {
        assertError(mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                        .accept(MediaType.TEXT_PLAIN).param("state", "MA").param("schoolYear", "K-6")),
                406, "REQUEST_NOT_SUPPORTED", null, "We couldn't process this request. Please check it and try again.");
    }

    @Test
    @DisplayName("/vaccines uses the contract too: non-numeric CVX code")
    void vaccines_nonNumericCvx() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/vaccines/cvx/abc")), 400, "INVALID_PARAMETER_TYPE", "cvxCode",
                "CVX code must be a whole number.")
                .andExpect(jsonPath("$.expectedType").value("integer"));
    }

    @Test
    @DisplayName("/vaccines uses the contract too: /search with no query")
    void vaccines_searchWithoutQuery() throws Exception {
        assertError(mockMvc.perform(get("/api/v1/vaccines/search")), 400, "MISSING_PARAMETER", "q",
                "Please enter a search term.");
    }

    @Test
    @DisplayName("/vaccines keeps its empty-body 404 for an unknown code or group")
    void vaccines_empty404IsUnchanged() throws Exception {
        mockMvc.perform(get("/api/v1/vaccines/cvx/999999"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(emptyString()));
        mockMvc.perform(get("/api/v1/vaccines/group/no-such-group"))
                .andExpect(status().isNotFound())
                .andExpect(content().string(emptyString()));
    }

    // ─── Request id ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("A valid client X-Request-Id is echoed in the header and the body")
    void requestId_validClientValueIsEchoed() throws Exception {
        mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                        .header("X-Request-Id", "client-id_1.2"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("X-Request-Id", "client-id_1.2"))
                .andExpect(jsonPath("$.requestId").value("client-id_1.2"));
    }

    @Test
    @DisplayName("An invalid client X-Request-Id is replaced by a generated id")
    void requestId_invalidClientValueIsReplaced() throws Exception {
        for (String bad : new String[]{"has spaces", "semi;colon", "x".repeat(65), "line\tbreak"}) {
            mockMvc.perform(post(SINGLE).contentType(MediaType.APPLICATION_JSON).content(PATIENT)
                            .header("X-Request-Id", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)))
                    .andExpect(jsonPath("$.requestId", matchesPattern(UUID_PATTERN)));
        }
    }

    @Test
    @DisplayName("Successful responses carry X-Request-Id too, with no change to the body")
    void requestId_presentOnSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/validate/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Request-Id", matchesPattern(UUID_PATTERN)))
                .andExpect(content().string("OK"));
    }
}
