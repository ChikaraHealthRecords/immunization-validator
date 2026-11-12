package com.immunization.validator.fhir;

import com.fasterxml.jackson.databind.JsonNode;
import com.immunization.validator.error.ClientText;
import com.immunization.validator.error.ErrorCode;
import com.immunization.validator.fhir.FhirMappingResult.MappedPatient;
import com.immunization.validator.fhir.FhirMappingResult.MappingWarning;
import com.immunization.validator.fhir.FhirMappingResult.PatientMapping;
import com.immunization.validator.fhir.FhirMappingResult.ResourceIssue;
import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.Patient;

import java.math.BigInteger;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns FHIR R4 resources into the existing {@link Patient} and {@link Immunization} models, plus a
 * report of everything that was left out or flagged. It only translates: it never interprets a
 * record clinically, and when the data is ambiguous it leans toward not counting a dose.
 *
 * The rules are in docs/fhir-validation.md (from the kit's FHIR-mapping-business-rules.md), and
 * the kit's fhir_mapper_reference.py is the executable spec: this class must give the same answers
 * on the fixtures in src/test/resources/fhir/.
 *
 * Plain Java on purpose: no Spring and no logging, so no patient data can reach a log from here.
 */
public class FhirPatientMapper {

    static final Set<String> CVX_SYSTEMS = Set.of(
            "http://hl7.org/fhir/sid/cvx",
            "urn:oid:2.16.840.1.113883.12.292");

    private static final Pattern PATIENT_REFERENCE =
            Pattern.compile("(?:^|/)Patient/([A-Za-z0-9\\-.]{1,64})(?:/_history/[^/]+)?$");
    private static final Pattern DIGITS = Pattern.compile("[0-9]+");
    private static final Pattern FULL_DATE = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");
    private static final Pattern PARTIAL_DATE = Pattern.compile("[0-9]{4}(-[0-9]{2})?");
    private static final String URN_UUID = "urn:uuid:";
    private static final String UNKNOWN_TYPE = "unknown";

    private final FhirInputReader reader = new FhirInputReader();

    /** Reads and maps FHIR input text. */
    public FhirMappingResult map(String text) {
        return map(reader.read(text));
    }

    public FhirMappingResult map(List<FhirInputReader.Entry> entries) {
        List<Draft> patients = new ArrayList<>();
        Map<String, Draft> index = new HashMap<>();
        Map<String, Integer> ignored = new LinkedHashMap<>();

        // First pass: patients, and a count of everything the validator doesn't use.
        for (FhirInputReader.Entry entry : entries) {
            JsonNode resource = entry.getResource();
            String type = resourceType(resource);
            if ("Patient".equals(type)) {
                Draft patient = new Draft(resource, null);
                patients.add(patient);
                if (truthy(resource.get("id"))) {
                    index.put("Patient/" + text(resource.get("id")), patient);
                }
                if (entry.getFullUrl() != null) {
                    index.put(entry.getFullUrl(), patient);
                }
            } else if (!"Immunization".equals(type)) {
                // Allergies, conditions, notes and the rest are not used. They never become exceptions.
                ignored.merge(type == null ? UNKNOWN_TYPE : type, 1, Integer::sum);
            }
        }

        boolean hasPatientResource = !patients.isEmpty();
        List<ResourceIssue> unattributed = new ArrayList<>();

        // Second pass: doses, attributed by reference only.
        for (int i = 0; i < entries.size(); i++) {
            JsonNode resource = entries.get(i).getResource();
            if (!"Immunization".equals(resourceType(resource))) {
                continue;
            }
            String resourceId = truthy(resource.get("id")) ? text(resource.get("id")) : "entry[" + i + "]";
            String reference = normalizeReference(patientReference(resource));
            if (reference == null) {
                // Never attach a dose to a patient by assumption, even if the input has only one Patient.
                throw new FhirMappingException(ErrorCode.VALIDATION_FAILED,
                        "entry[" + i + "].resource.patient", "NO_PATIENT_REFERENCE");
            }
            Draft patient = index.get(reference);
            if (patient == null && !hasPatientResource) {
                // Immunization-only input: group by the reference. No Patient means no birth date.
                String id = reference.startsWith("Patient/")
                        ? reference.substring("Patient/".length())
                        : reference.substring(URN_UUID.length());
                patient = new Draft(null, id);
                patient.warn("NO_PATIENT_RESOURCE", id);
                patients.add(patient);
                index.put(reference, patient);
            }
            if (patient == null) {
                unattributed.add(new ResourceIssue(resourceId, "REFERS_TO_OTHER_PATIENT"));
                continue;
            }
            addDose(patient, resource, resourceId);
        }

        if (patients.isEmpty()) {
            throw new FhirMappingException(ErrorCode.VALIDATION_FAILED, "entry", "NO_PATIENT_OR_IMMUNIZATION");
        }

        List<MappedPatient> out = new ArrayList<>();
        for (Draft draft : patients) {
            out.add(toMappedPatient(draft));
        }
        return new FhirMappingResult(out, unattributed, ignored);
    }

    // ─── Doses ────────────────────────────────────────────────────────────────

    private void addDose(Draft patient, JsonNode resource, String resourceId) {
        patient.received++;

        String status = textOrNull(resource.get("status"));
        if ("entered-in-error".equals(status)) {
            patient.exclude(resourceId, "STATUS_ENTERED_IN_ERROR");
            return;
        }
        if ("not-done".equals(status)) {
            // Not given. Never a dose, and never an exception either.
            patient.exclude(resourceId, "STATUS_NOT_DONE");
            patient.warn("NOT_DONE_NOT_COUNTED", resourceId);
            return;
        }
        if (!"completed".equals(status)) {
            patient.exclude(resourceId, "MISSING_OR_UNKNOWN_STATUS");
            patient.warn("MISSING_STATUS", resourceId);
            return;
        }
        JsonNode subpotent = resource.get("isSubpotent");
        if (subpotent != null && subpotent.isBoolean() && subpotent.booleanValue()) {
            patient.exclude(resourceId, "SUBPOTENT");
            patient.warn("SUBPOTENT_NOT_COUNTED", resourceId);
            return;
        }

        Immunization dose = new Immunization();

        CvxChoice cvx = chooseCvx(resource.get("vaccineCode"));
        if (cvx.code != null) {
            dose.setCvxCode(cvx.code);
        } else {
            // Passed on without a code: the engine sets it aside and the result becomes UNDETERMINED.
            patient.warn(cvx.warning, resourceId);
        }

        JsonNode when = resource.get("occurrenceDateTime");
        if (when != null && !when.isNull()) {
            // Trimmed only. A partial date is never padded: the engine sets it aside.
            dose.setOccurrenceDateTime(text(when).strip());
            if (when.isTextual() && isPartialDate(when.textValue())) {
                patient.warn("PARTIAL_DATE_NOT_PADDED", resourceId);
            }
        } else {
            JsonNode occurrenceString = resource.get("occurrenceString");
            if (occurrenceString != null && !occurrenceString.isNull()) {
                // "June 2020" is not parsed.
                patient.warn("DATE_NOT_STRUCTURED", resourceId);
            }
        }

        if (truthy(resource.get("lotNumber"))) {
            dose.setLotNumber(text(resource.get("lotNumber")));
        }

        JsonNode protocols = resource.get("protocolApplied");
        if (protocols != null && protocols.isArray()) {
            for (JsonNode protocol : protocols) {
                JsonNode number = protocol.isObject() ? protocol.get("doseNumberPositiveInt") : null;
                if (number != null && number.isIntegralNumber() && number.canConvertToInt() && number.intValue() > 0) {
                    dose.setDoseNumber(number.intValue());
                    break;
                }
            }
        }

        patient.doses.add(dose);
    }

    /** Chosen by coding system, never by position. Two different codes, or a non-numeric one, give none. */
    private CvxChoice chooseCvx(JsonNode vaccineCode) {
        Set<Integer> codes = new LinkedHashSet<>();
        boolean bad = false;
        JsonNode codings = vaccineCode != null && vaccineCode.isObject() ? vaccineCode.get("coding") : null;
        if (codings != null && codings.isArray()) {
            for (JsonNode coding : codings) {
                if (!coding.isObject() || !CVX_SYSTEMS.contains(textOrNull(coding.get("system")))) {
                    continue;
                }
                Integer code = cvxNumber(coding.get("code"));
                if (code != null) {
                    codes.add(code);
                } else {
                    bad = true;
                }
            }
        }
        if (codes.size() > 1) {
            return new CvxChoice(null, "AMBIGUOUS_CVX");
        }
        if (codes.size() == 1) {
            return new CvxChoice(codes.iterator().next(), null);
        }
        return new CvxChoice(null, bad ? "NON_NUMERIC_CVX" : "NO_CVX_CODE");
    }

    /** "08" and 8 are both 8. Anything that isn't plain digits, or doesn't fit an int, is not a code. */
    private static Integer cvxNumber(JsonNode code) {
        if (code == null || code.isNull() || code.isContainerNode()) {
            return null;
        }
        String raw = text(code).strip();
        if (!DIGITS.matcher(raw).matches()) {
            return null;
        }
        BigInteger value = new BigInteger(raw);
        return value.bitLength() < 32 ? value.intValue() : null;
    }

    // ─── Patients ─────────────────────────────────────────────────────────────

    private MappedPatient toMappedPatient(Draft draft) {
        String id = draft.syntheticId != null ? draft.syntheticId : patientId(draft.resource);
        if (id == null) {
            throw new FhirMappingException(ErrorCode.VALIDATION_FAILED, "id", "PATIENT_WITHOUT_ID");
        }

        // Only id and birthDate leave the FHIR Patient: never name, address, telecom or other identifiers.
        Patient patient = new Patient();
        patient.setId(id);

        JsonNode birthDate = draft.resource != null ? draft.resource.get("birthDate") : null;
        if (truthy(birthDate)) {
            // Only a full, real date is sent. Anything else is withheld: the engine couldn't use it,
            // and it would write the raw value to the server log when it tried.
            if (birthDate.isTextual() && isFullDate(birthDate.textValue())) {
                patient.setBirthDate(birthDate.textValue());
            } else if (birthDate.isTextual() && isPartialDate(birthDate.textValue())) {
                draft.warn("BIRTHDATE_PARTIAL", id);
            } else {
                draft.warn("BIRTHDATE_INVALID", id);
            }
        }
        if (draft.received == 0) {
            draft.warn("NO_IMMUNIZATION_RESOURCES", id);
        }

        patient.setImmunizations(draft.doses);
        // Never inferred from FHIR: an accepted exception satisfies a requirement outright.
        patient.setExceptions(new ArrayList<>());

        return new MappedPatient(patient,
                new PatientMapping(draft.received, draft.doses.size(), draft.excluded, draft.warnings));
    }

    /** Patient.id, else the first identifier with a value, else null. */
    private static String patientId(JsonNode resource) {
        if (truthy(resource.get("id"))) {
            return text(resource.get("id"));
        }
        JsonNode identifiers = resource.get("identifier");
        if (identifiers != null && identifiers.isArray()) {
            for (JsonNode identifier : identifiers) {
                if (identifier.isObject() && truthy(identifier.get("value"))) {
                    return text(identifier.get("value"));
                }
            }
        }
        return null;
    }

    // ─── References ───────────────────────────────────────────────────────────

    private static String patientReference(JsonNode immunization) {
        JsonNode patient = immunization.get("patient");
        return patient != null && patient.isObject() ? textOrNull(patient.get("reference")) : null;
    }

    /**
     * "Patient/p1", "http://x/fhir/Patient/p1" and "Patient/p1/_history/2" become "Patient/p1";
     * a urn:uuid is kept as is, to be matched to a fullUrl; anything else is null.
     */
    static String normalizeReference(String reference) {
        if (reference == null || reference.isEmpty()) {
            return null;
        }
        if (reference.startsWith(URN_UUID)) {
            return reference;
        }
        Matcher matcher = PATIENT_REFERENCE.matcher(reference);
        return matcher.find() ? "Patient/" + matcher.group(1) : null;
    }

    // ─── Dates ────────────────────────────────────────────────────────────────

    /** Exactly YYYY-MM-DD and a day that exists. "2019-02-30" is not. */
    static boolean isFullDate(String value) {
        if (value == null || !FULL_DATE.matcher(value).matches()) {
            return false;
        }
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** "2020" or "2020-06", surrounding spaces ignored. */
    static boolean isPartialDate(String value) {
        return value != null && PARTIAL_DATE.matcher(value.strip()).matches();
    }

    // ─── JSON helpers ─────────────────────────────────────────────────────────

    private static String resourceType(JsonNode resource) {
        JsonNode type = resource.get("resourceType");
        return type == null || type.isNull() ? null : text(type);
    }

    /** Present and not empty, false, zero or null: the reference implementation's test for a value. */
    private static boolean truthy(JsonNode node) {
        if (node == null || node.isNull() || node.isMissingNode()) {
            return false;
        }
        if (node.isTextual()) {
            return !node.textValue().isEmpty();
        }
        if (node.isBoolean()) {
            return node.booleanValue();
        }
        if (node.isNumber()) {
            return node.decimalValue().signum() != 0;
        }
        return node.size() > 0;
    }

    /**
     * A value as text. Ids, resource types and dates can be echoed in the response, so a broken
     * character (half of an emoji) becomes "?" here rather than failing the whole response.
     */
    private static String text(JsonNode node) {
        String value;
        if (node.isTextual()) {
            value = node.textValue();
        } else {
            value = node.isValueNode() ? node.asText() : node.toString();
        }
        return ClientText.wellFormed(value);
    }

    private static String textOrNull(JsonNode node) {
        return node != null && node.isTextual() ? node.textValue() : null;
    }

    // ─── Working state ────────────────────────────────────────────────────────

    /** A patient being built: the FHIR Patient (or just an id taken from references) and its doses. */
    private static final class Draft {
        private final JsonNode resource;
        private final String syntheticId;
        private final List<Immunization> doses = new ArrayList<>();
        private final List<ResourceIssue> excluded = new ArrayList<>();
        private final List<MappingWarning> warnings = new ArrayList<>();
        private int received;

        private Draft(JsonNode resource, String syntheticId) {
            this.resource = resource;
            this.syntheticId = syntheticId;
        }

        private void exclude(String resourceId, String reason) {
            excluded.add(new ResourceIssue(resourceId, reason));
        }

        private void warn(String code, String resourceId) {
            warnings.add(new MappingWarning(code, resourceId));
        }
    }

    private static final class CvxChoice {
        private final Integer code;
        private final String warning;

        private CvxChoice(Integer code, String warning) {
            this.code = code;
            this.warning = warning;
        }
    }
}
