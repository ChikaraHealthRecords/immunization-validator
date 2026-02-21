package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Response object for immunization validation.
 *
 * Version 3.0 - Uses tri-state ComplianceStatus instead of boolean
 *
 * @author Saakad
 * @version 3.0
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResponse {

    /**
     * Patient identifier
     */
    private String patientId;

    /**
     * Compliance status: VALID, INVALID, or UNDETERMINED
     */
    private ComplianceStatus status;

    /**
     * @deprecated Use status instead. Kept for backward compatibility.
     */
    @Deprecated
    private Boolean valid;

    /**
     * List of requirements that are not met (only if status is INVALID)
     */
    private List<UnmetRequirement> unmetRequirements;

    /**
     * List of conditions that could not be evaluated (only if status is UNDETERMINED)
     */
    private List<UndeterminedCondition> undeterminedConditions;

    /**
     * Human-readable message explaining the validation result
     */
    private String message;

    /**
     * Additional warnings (e.g., missing data that didn't prevent validation)
     */
    private List<String> warnings;

    /**
     * Validation metadata
     */
    private ValidationMetadata metadata;

    /**
     * For backward compatibility - populate the deprecated 'valid' field
     */
    public void setStatusWithBackwardCompatibility(ComplianceStatus status) {
        this.status = status;
        // Populate deprecated field for clients still using it
        // UNDETERMINED is treated as false for safety
        this.valid = (status == ComplianceStatus.VALID);
    }
}