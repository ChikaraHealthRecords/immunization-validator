package com.immunization.validator.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.immunization.validator.error.ErrorCode;
import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

/**
 * The FHIR mapper against the kit's 16 made-up fixtures (src/test/resources/fhir/), and the
 * behaviours the kit leaves open but that were decided (see docs/fhir-validation.md).
 * Plain unit tests: no Spring, no database. All data is made up.
 */
@DisplayName("FHIR patient mapper")
class FhirPatientMapperTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String CVX = "http://hl7.org/fhir/sid/cvx";

    /** Made-up identifying values that are in the fixtures and must never come out of the mapper. */
    private static final List<String> IDENTIFYING = List.of(
            "Testcase", "Alex", "Example Street", "555-0100", "Testville", "MRN-0001", "ALT-0001");

    private final FhirPatientMapper mapper = new FhirPatientMapper();

    // ─── Fixtures ─────────────────────────────────────────────────────────────

    static Stream<String> fixtures() throws IOException, URISyntaxException {
        try (Stream<Path> files = Files.list(fixtureDir())) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> name.endsWith(".expected.json"))
                    .map(name -> name.substring(0, name.length() - ".expected.json".length()))
                    .sorted()
                    .collect(Collectors.toList())
                    .stream();
        }
    }

    @Test
    @DisplayName("All 16 kit fixtures are present")
    void allFixturesPresent() throws Exception {
        assertThat(fixtures()).hasSize(16);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("Fixture matches its expected answer")
    void fixtureMatchesExpected(String fixture) throws Exception {
        JsonNode expected = JSON.readTree(read(fixture + ".expected.json"));
        String input = read(inputFile(fixture));

        if (expected.has("error")) {
            JsonNode error = expected.get("error");
            FhirMappingException ex = catchThrowableOfType(() -> mapper.map(input), FhirMappingException.class);
            assertThat(ex).as("expected a mapping error").isNotNull();
            assertThat(ex.getErrorCode().name()).isEqualTo(error.get("errorCode").asText());
            assertThat(ex.getField()).isEqualTo(error.get("field").isNull() ? null : error.get("field").asText());
            assertThat(ex.getReason()).isEqualTo(error.get("reason").asText());
            return;
        }

        JsonNode actual = withoutNulls(JSON.valueToTree(mapper.map(input)));
        assertThat(actual).isEqualTo(expected);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fixtures")
    @DisplayName("Output never contains a name, address, phone or other identifier")
    void noIdentifyingDataLeaves(String fixture) throws Exception {
        String input = read(inputFile(fixture));
        FhirMappingResult result;
        try {
            result = mapper.map(input);
        } catch (FhirMappingException expectedForErrorFixtures) {
            return;
        }
        String serialized = JSON.writeValueAsString(result);
        for (String value : IDENTIFYING) {
            assertThat(serialized).doesNotContain(value);
        }
    }

    @Test
    @DisplayName("The privacy check would notice a leak (fixture 01 does contain the made-up name)")
    void privacyCheckCanFail() throws Exception {
        assertThat(read("01-valid-collection-bundle.json")).contains("Testcase", "Example Street", "555-0100");
        assertThat(read("12-epic-shaped-bulk.ndjson")).contains("MRN-0001", "Testcase");
    }

    // ─── Input forms ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Input forms")
    class InputForms {

        @Test
        @DisplayName("NDJSON with two or more lines is read line by line")
        void ndjson() {
            String text = patient("nd-1", "2019-01-01") + "\n\n" + imm("i-1", "Patient/nd-1", "20", "2020-01-01") + "\r\n";
            FhirMappingResult result = mapper.map(text);
            assertThat(result.getPatients()).hasSize(1);
            assertThat(result.getPatients().get(0).getMapping().getMapped()).isEqualTo(1);
        }

        @Test
        @DisplayName("One line that isn't JSON is NOT_JSON, not NDJSON")
        void singleBadLine() {
            assertMalformed("{\"resourceType\": \"Patient\", ", FhirInputReader.NOT_JSON);
            assertMalformed("", FhirInputReader.NOT_JSON);
            assertMalformed("   ", FhirInputReader.NOT_JSON);
        }

        @Test
        @DisplayName("Two resources on one line fail (trailing tokens are not ignored)")
        void trailingTokens() {
            assertMalformed(patient("a", "2019-01-01") + " " + patient("b", "2019-01-01"), FhirInputReader.NOT_JSON);
        }

        @Test
        @DisplayName("A JSON array item that isn't an object is NOT_A_RESOURCE")
        void nonObjectArrayItem() {
            assertMalformed("[" + patient("a", "2019-01-01") + ", 5]", FhirInputReader.NOT_A_RESOURCE);
            assertMalformed(patient("a", "2019-01-01") + "\n\"text\"", FhirInputReader.NOT_A_RESOURCE);
        }

        @Test
        @DisplayName("A bare value that isn't an object or array is NOT_AN_OBJECT")
        void scalar() {
            assertMalformed("42", FhirInputReader.NOT_AN_OBJECT);
            assertMalformed("null", FhirInputReader.NOT_AN_OBJECT);
        }

        @Test
        @DisplayName("A leading byte-order mark is ignored")
        void byteOrderMark() {
            assertThat(mapper.map("﻿" + patient("bom-1", "2019-01-01")).getPatients()).hasSize(1);
        }
    }

    // ─── Rules ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Rules")
    class Rules {

        @Test
        @DisplayName("Rule 2: no id and no identifier is a request error on field id")
        void noIdAtAll() {
            FhirMappingException ex = catchThrowableOfType(
                    () -> mapper.map("{\"resourceType\":\"Patient\",\"birthDate\":\"2019-01-01\"}"),
                    FhirMappingException.class);
            assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_FAILED);
            assertThat(ex.getField()).isEqualTo("id");
            assertThat(ex.getReason()).isEqualTo("PATIENT_WITHOUT_ID");
        }

        @Test
        @DisplayName("Rule 2: the first identifier with a value is used when Patient.id is missing")
        void identifierFallback() {
            String text = "{\"resourceType\":\"Patient\",\"identifier\":[{\"system\":\"x\"},{\"value\":\"made-up-77\"}]}";
            assertThat(onlyPatient(mapper.map(text)).getId()).isEqualTo("made-up-77");
        }

        @Test
        @DisplayName("Rule 2: a whitespace id is passed on unchanged (the controller turns it into an error item)")
        void blankIdPassedOn() {
            assertThat(onlyPatient(mapper.map(patient("   ", "2019-01-01"))).getId()).isEqualTo("   ");
        }

        @Test
        @DisplayName("Rule 4: allergies and not-done reasons never become exceptions")
        void neverExceptions() {
            String text = "[" + patient("ex-1", "2019-01-01") + ","
                    + "{\"resourceType\":\"AllergyIntolerance\",\"id\":\"al\",\"patient\":{\"reference\":\"Patient/ex-1\"}},"
                    + "{\"resourceType\":\"Immunization\",\"id\":\"nd\",\"status\":\"not-done\","
                    + "\"statusReason\":{\"coding\":[{\"code\":\"MEDPREC\"}]},"
                    + "\"vaccineCode\":{\"coding\":[{\"system\":\"" + CVX + "\",\"code\":\"20\"}]},"
                    + "\"patient\":{\"reference\":\"Patient/ex-1\"},\"occurrenceDateTime\":\"2020-01-01\"}]";
            FhirMappingResult result = mapper.map(text);
            assertThat(onlyPatient(result).getExceptions()).isEmpty();
            assertThat(onlyPatient(result).getImmunizations()).isEmpty();
            assertThat(result.getIgnored()).containsEntry("AllergyIntolerance", 1);
        }

        @Test
        @DisplayName("Rule 7: only a full, real birth date is sent")
        void birthDates() {
            assertBirthDate("2019-01-31", "2019-01-31", null);
            assertBirthDate("2019-04", null, "BIRTHDATE_PARTIAL");
            assertBirthDate("2019", null, "BIRTHDATE_PARTIAL");
            assertBirthDate("2019-02-30", null, "BIRTHDATE_INVALID");
            assertBirthDate("2019-13-01", null, "BIRTHDATE_INVALID");
            assertBirthDate("2019-01-01T00:00:00", null, "BIRTHDATE_INVALID");
            assertBirthDate("01/02/2019", null, "BIRTHDATE_INVALID");
            assertBirthDate(" 2019-01-01", null, "BIRTHDATE_INVALID");
        }

        @Test
        @DisplayName("Rule 8: numeric JSON codes are accepted; the same code twice is not ambiguous")
        void cvxCodes() {
            assertThat(onlyDose("{\"system\":\"" + CVX + "\",\"code\":20}").getCvxCode()).isEqualTo(20);
            assertThat(onlyDose("{\"system\":\"" + CVX + "\",\"code\":\"20\"},{\"system\":\"" + CVX
                    + "\",\"code\":\"020\"}").getCvxCode()).isEqualTo(20);
        }

        @Test
        @DisplayName("Rule 8: a code too big for a number, or with a decimal point, is not a code")
        void cvxNotANumber() {
            FhirMappingResult big = mapper.map(withPatient(imm("i", "Patient/p", "99999999999", "2020-01-01")));
            assertThat(onlyPatient(big).getImmunizations().get(0).getCvxCode()).isNull();
            assertThat(big.getPatients().get(0).getMapping().getWarnings().get(0).getCode()).isEqualTo("NON_NUMERIC_CVX");
            assertThat(onlyDose("{\"system\":\"" + CVX + "\",\"code\":20.5}").getCvxCode()).isNull();
        }

        @Test
        @DisplayName("Rule 11: a reference id must match [A-Za-z0-9.-]{1,64}")
        void referencePattern() {
            assertThat(FhirPatientMapper.normalizeReference("Patient/a.b-C9")).isEqualTo("Patient/a.b-C9");
            assertThat(FhirPatientMapper.normalizeReference("https://x.org/fhir/Patient/p1/_history/3")).isEqualTo("Patient/p1");
            assertThat(FhirPatientMapper.normalizeReference("Patient/" + "a".repeat(64))).isNotNull();
            assertThat(FhirPatientMapper.normalizeReference("Patient/" + "a".repeat(65))).isNull();
            assertThat(FhirPatientMapper.normalizeReference("Patient/a_b")).isNull();
            assertThat(FhirPatientMapper.normalizeReference("Practitioner/p1")).isNull();
            assertThat(FhirPatientMapper.normalizeReference("")).isNull();
        }

        @Test
        @DisplayName("Rule 11: a dose whose reference doesn't match the pattern fails the request")
        void badReferenceFails() {
            String text = withPatient(imm("i", "Patient/a_b", "20", "2020-01-01"));
            FhirMappingException ex = catchThrowableOfType(() -> mapper.map(text), FhirMappingException.class);
            assertThat(ex.getField()).isEqualTo("entry[1].resource.patient");
            assertThat(ex.getReason()).isEqualTo("NO_PATIENT_REFERENCE");
        }

        @Test
        @DisplayName("Rule 17: an Immunization with no id is reported as entry[i]")
        void entryIndexWhenNoId() {
            String noId = "{\"resourceType\":\"Immunization\",\"status\":\"not-done\",\"patient\":{\"reference\":\"Patient/p\"}}";
            FhirMappingResult result = mapper.map(withPatient(noId));
            assertThat(result.getPatients().get(0).getMapping().getExcluded().get(0).getResourceId()).isEqualTo("entry[1]");
        }

        @Test
        @DisplayName("Rule 6: dates are trimmed and otherwise passed on as sent")
        void datesTrimmedOnly() {
            FhirMappingResult result = mapper.map("[" + patient("p", "2019-01-01") + ","
                    + imm("a", "Patient/p", "20", " 2020-06-10 ") + "," + imm("b", "Patient/p", "20", "2020-06 ") + "]");
            List<Immunization> doses = onlyPatient(result).getImmunizations();
            assertThat(doses.get(0).getOccurrenceDateTime()).isEqualTo("2020-06-10");
            assertThat(doses.get(1).getOccurrenceDateTime()).isEqualTo("2020-06");
            assertThat(result.getPatients().get(0).getMapping().getWarnings())
                    .extracting(FhirMappingResult.MappingWarning::getCode).containsExactly("PARTIAL_DATE_NOT_PADDED");
        }

        @Test
        @DisplayName("Rule 13: doseNumberPositiveInt accepts only whole numbers above 0")
        void doseNumbers() {
            assertThat(doseNumber("2")).isEqualTo(2);
            assertThat(doseNumber("0")).isNull();
            assertThat(doseNumber("-1")).isNull();
            assertThat(doseNumber("2.0")).isNull();
            assertThat(doseNumber("\"3\"")).isNull();
            assertThat(doseNumber("true")).isNull();
            assertThat(doseNumber("99999999999")).isNull();
        }

        @Test
        @DisplayName("Rule 18: a resource with no resourceType is counted as \"unknown\"")
        void unknownResourceType() {
            FhirMappingResult result = mapper.map("[" + patient("p", "2019-01-01") + ",{\"id\":\"x\"},{\"id\":\"y\"}]");
            assertThat(result.getIgnored()).isEqualTo(Map.of("unknown", 2));
        }

        @Test
        @DisplayName("A Bundle entry with no resource is counted as unknown, not a crash")
        void emptyBundleEntry() {
            String text = "{\"resourceType\":\"Bundle\",\"entry\":[{\"resource\":" + patient("p", "2019-01-01") + "},{},null]}";
            assertThat(mapper.map(text).getIgnored()).isEqualTo(Map.of("unknown", 2));
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void assertMalformed(String text, String reason) {
        assertThatThrownBy(() -> mapper.map(text))
                .isInstanceOfSatisfying(FhirMappingException.class, ex -> {
                    assertThat(ex.getErrorCode()).isEqualTo(ErrorCode.MALFORMED_REQUEST);
                    assertThat(ex.getField()).isNull();
                    assertThat(ex.getReason()).isEqualTo(reason);
                });
    }

    private void assertBirthDate(String sent, String expected, String warning) {
        FhirMappingResult result = mapper.map(withPatient(sent, imm("i", "Patient/p", "20", "2020-01-01")));
        assertThat(onlyPatient(result).getBirthDate()).as(sent).isEqualTo(expected);
        List<String> warnings = result.getPatients().get(0).getMapping().getWarnings().stream()
                .map(FhirMappingResult.MappingWarning::getCode).collect(Collectors.toList());
        if (warning == null) {
            assertThat(warnings).as(sent).isEmpty();
        } else {
            assertThat(warnings).as(sent).containsExactly(warning);
        }
    }

    private Integer doseNumber(String json) {
        String dose = "{\"resourceType\":\"Immunization\",\"id\":\"i\",\"status\":\"completed\","
                + "\"patient\":{\"reference\":\"Patient/p\"},\"occurrenceDateTime\":\"2020-01-01\","
                + "\"protocolApplied\":[{\"doseNumberPositiveInt\":" + json + "}]}";
        return onlyPatient(mapper.map(withPatient(dose))).getImmunizations().get(0).getDoseNumber();
    }

    private Immunization onlyDose(String codings) {
        String dose = "{\"resourceType\":\"Immunization\",\"id\":\"i\",\"status\":\"completed\","
                + "\"vaccineCode\":{\"coding\":[" + codings + "]},"
                + "\"patient\":{\"reference\":\"Patient/p\"},\"occurrenceDateTime\":\"2020-01-01\"}";
        return onlyPatient(mapper.map(withPatient(dose))).getImmunizations().get(0);
    }

    private static Patient onlyPatient(FhirMappingResult result) {
        assertThat(result.getPatients()).hasSize(1);
        return result.getPatients().get(0).getChikara();
    }

    private static String withPatient(String immunization) {
        return withPatient("2019-01-01", immunization);
    }

    private static String withPatient(String birthDate, String immunization) {
        return "[" + patient("p", birthDate) + "," + immunization + "]";
    }

    private static String patient(String id, String birthDate) {
        return "{\"resourceType\":\"Patient\",\"id\":\"" + id + "\",\"birthDate\":\"" + birthDate + "\"}";
    }

    private static String imm(String id, String reference, String cvx, String date) {
        return "{\"resourceType\":\"Immunization\",\"id\":\"" + id + "\",\"status\":\"completed\","
                + "\"vaccineCode\":{\"coding\":[{\"system\":\"" + CVX + "\",\"code\":\"" + cvx + "\"}]},"
                + "\"patient\":{\"reference\":\"" + reference + "\"},\"occurrenceDateTime\":\"" + date + "\"}";
    }

    /** The mapper's tree with null-valued fields dropped (the model classes have optional fields). */
    static JsonNode withoutNulls(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = JSON.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (!field.getValue().isNull()) {
                    copy.set(field.getKey(), withoutNulls(field.getValue()));
                }
            }
            return copy;
        }
        if (node.isArray()) {
            ArrayNode copy = JSON.createArrayNode();
            node.forEach(item -> copy.add(withoutNulls(item)));
            return copy;
        }
        return node;
    }

    private static String inputFile(String fixture) throws Exception {
        return Files.exists(fixtureDir().resolve(fixture + ".ndjson")) ? fixture + ".ndjson" : fixture + ".json";
    }

    private static Path fixtureDir() throws URISyntaxException {
        URL url = FhirPatientMapperTest.class.getResource("/fhir");
        if (url == null) {
            throw new IllegalStateException("src/test/resources/fhir is missing");
        }
        return Paths.get(url.toURI());
    }

    static String read(String name) throws Exception {
        return Files.readString(fixtureDir().resolve(name), StandardCharsets.UTF_8);
    }
}
