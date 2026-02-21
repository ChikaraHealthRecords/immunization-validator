package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the compliance status of a patient's immunization record.
 *
 * This is the top-level status returned in API responses.
 *
 * @author Saakad
 * @version 3.0
 */
public enum ComplianceStatus {
    /**
     * Patient is compliant with all immunization requirements.
     * All requirements are satisfied.
     */
    VALID("valid"),

    /**
     * Patient is not compliant with immunization requirements.
     * One or more requirements are not satisfied.
     */
    INVALID("invalid"),

    /**
     * Cannot determine compliance status.
     * This occurs when:
     * - Required data is missing or invalid
     * - Date conditions cannot be parsed
     * - System errors prevent evaluation
     */
    UNDETERMINED("undetermined");

    private final String value;

    ComplianceStatus(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Parse from string value
     */
    public static ComplianceStatus fromString(String value) {
        for (ComplianceStatus status : values()) {
            if (status.value.equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown compliance status: " + value);
    }

    /**
     * Check if this status represents compliance
     */
    public boolean isCompliant() {
        return this == VALID;
    }

    /**
     * Check if this status can be definitively determined
     */
    public boolean isDeterminate() {
        return this != UNDETERMINED;
    }
}