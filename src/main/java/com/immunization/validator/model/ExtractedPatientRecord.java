package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Top-level shape of the JSON schema submitted to and returned by
 * LlamaExtract for an immunization summary document. Deserialization
 * target for {@code ExtractionJob.resultJson}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedPatientRecord {

    @JsonProperty("patient")
    private ExtractedPatientInfo patient;

    @JsonProperty("immunizations")
    private List<ExtractedImmunizationEntry> immunizations;
}
