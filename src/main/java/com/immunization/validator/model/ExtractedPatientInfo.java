package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Patient fields of the JSON schema submitted to LlamaExtract for
 * immunization summary documents.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedPatientInfo {

    @JsonProperty("name")
    private String name;

    /** Requested from LlamaExtract as ISO 8601 (YYYY-MM-DD); may still arrive in the document's native format. */
    @JsonProperty("dateOfBirth")
    private String dateOfBirth;

    @JsonProperty("medicalRecordNumber")
    private String medicalRecordNumber;
}
