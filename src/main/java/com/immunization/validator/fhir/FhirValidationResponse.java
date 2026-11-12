package com.immunization.validator.fhir;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.immunization.validator.model.ValidationResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Response of POST /api/v1/validate/fhir. Always this shape, even for one patient.
 * See docs/fhir-validation.md.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"results", "mapping", "unattributed", "ignoredResourceTypes", "message", "failedCount"})
public class FhirValidationResponse {

    /** One validation result per patient, as on /batch. A failed patient has an error and no status. */
    private List<ValidationResponse> results;

    /** The mapping report of each patient, in the same order as results. */
    private List<FhirMappingResult.PatientMapping> mapping;

    /** Doses that refer to a patient who isn't in the input. They count for nobody. */
    private List<FhirMappingResult.ResourceIssue> unattributed;

    /** Resource types that were ignored, with how many of each arrived. */
    private Map<String, Integer> ignoredResourceTypes;

    /** Present only when at least one patient failed. */
    private String message;

    /** How many patients failed. Present only when at least one did. */
    private Integer failedCount;
}
