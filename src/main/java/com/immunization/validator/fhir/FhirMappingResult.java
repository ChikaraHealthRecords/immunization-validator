package com.immunization.validator.fhir;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.immunization.validator.model.Patient;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * What {@link FhirPatientMapper} produced: the patients to validate, each with its mapping report,
 * the doses that belong to a patient not in the input, and the resource types that were ignored.
 *
 * Serializes to the shape of the kit's *.expected.json files.
 */
@Getter
@AllArgsConstructor
@JsonPropertyOrder({"patients", "unattributed", "ignored"})
public class FhirMappingResult {

    private final List<MappedPatient> patients;

    /** Doses that refer to a patient who isn't in the input. They count for nobody. */
    private final List<ResourceIssue> unattributed;

    /** Resource types the validator doesn't use, with how many of each arrived. */
    private final Map<String, Integer> ignored;

    /** One patient, in the existing model, plus why each of its resources did or didn't count. */
    @Getter
    @AllArgsConstructor
    @JsonPropertyOrder({"chikara", "mapping"})
    public static class MappedPatient {
        private final Patient chikara;
        private final PatientMapping mapping;
    }

    /** The mapping report for one patient. */
    @Getter
    @AllArgsConstructor
    @JsonPropertyOrder({"received", "mapped", "excluded", "warnings"})
    public static class PatientMapping {
        /** Immunization resources attributed to this patient. */
        private final int received;
        /** How many of them were passed to the validator. */
        private final int mapped;
        private final List<ResourceIssue> excluded;
        private final List<MappingWarning> warnings;
    }

    /** A resource that was left out, with a reason code. */
    @Getter
    @AllArgsConstructor
    @JsonPropertyOrder({"resourceId", "reason"})
    public static class ResourceIssue {
        private final String resourceId;
        private final String reason;
    }

    /** Something worth knowing about a resource that didn't stop it being used. */
    @Getter
    @AllArgsConstructor
    @JsonPropertyOrder({"code", "resourceId"})
    public static class MappingWarning {
        private final String code;
        private final String resourceId;
    }
}
