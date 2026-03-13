package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Response object for immunization validation.
 *
 * Version 3.0 - Uses tri-state ComplianceStatus instead of boolean.
 *
 * @author Saakad
 * @version 3.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationResponse {

    /** Patient identifier */
    private String patientId;

    /** Compliance status: VALID, INVALID, or UNDETERMINED */
    private ComplianceStatus status;

    /**
     * @deprecated Use status instead. Kept for backward compatibility.
     */
    @Deprecated
    private Boolean valid;

    /** Requirements that are not met (only if status is INVALID) */
    private List<UnmetRequirement> unmetRequirements;

    /** Conditions that could not be evaluated (only if status is UNDETERMINED) */
    private List<UndeterminedCondition> undeterminedConditions;

    /** Human-readable message explaining the validation result */
    private String message;

    /** Additional warnings (e.g. missing data that didn't block validation) */
    private List<String> warnings;

    /** Validation metadata */
    private ValidationMetadata metadata;

    /**
     * Sets status and populates the deprecated {@code valid} field
     * so existing callers that check {@code valid} continue to work.
     *
     * UNDETERMINED is treated as false for safety.
     */
    public void setStatusWithBackwardCompatibility(ComplianceStatus status) {
        this.status = status;
        this.valid = (status == ComplianceStatus.VALID);
    }
}
