package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response model for batch validation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BatchValidationResponse {
    
    /**
     * List of validation responses for each patient
     */
    @JsonProperty("results")
    private List<ValidationResponse> results;

    /** Present only when at least one record failed. */
    @JsonProperty("message")
    private String message;

    /** How many records failed. Present only when at least one did. */
    @JsonProperty("failedCount")
    private Integer failedCount;
}

