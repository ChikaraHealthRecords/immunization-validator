package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * API response shape for the extraction endpoints.
 * validationResult is populated only when status == COMPLETED;
 * errorMessage only when status == FAILED.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExtractionJobResponse {

    private String jobId;

    private ExtractionJobStatus status;

    private JsonNode validationResult;

    private String errorMessage;
}
