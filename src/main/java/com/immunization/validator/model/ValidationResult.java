package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Represents the result of a validation check.
 *
 * Using a tri-state pattern allows us to distinguish between:
 * - Conditions that are definitely met (SATISFIED)
 * - Conditions that are definitely not met (NOT_SATISFIED)
 * - Conditions that cannot be evaluated (UNDETERMINED)
 *
 * ✅ V3 semantics (as used in your ValidationService):
 * - AND: UNDETERMINED wins over NOT_SATISFIED (we don't know, so can't say invalid)
 * - OR : SATISFIED wins (any satisfied path passes), otherwise UNDETERMINED wins over NOT_SATISFIED
 *
 * Production / “factory standard” additions:
 * - Stable JSON values via @JsonValue
 * - Safe parsing via @JsonCreator (no runtime failures on bad input; returns UNDETERMINED)
 * - Helper methods to reduce scattered conditional logic
 *
 * @author Saakad
 * @version 3.0
 */
public enum ValidationResult {
    /**
     * The validation condition is satisfied.
     * Example: Patient has 4 DTaP doses with 4th on 4th birthday
     */
    SATISFIED("satisfied", "Condition satisfied"),

    /**
     * The validation condition is not satisfied.
     * Example: Patient has 3 DTaP doses but needs 4
     */
    NOT_SATISFIED("not_satisfied", "Condition not satisfied"),

    /**
     * The validation condition cannot be evaluated.
     * Example: Date condition syntax is unparseable, or required data is missing
     */
    UNDETERMINED("undetermined", "Condition cannot be determined");

    private final String value;        // JSON / external string representation
    private final String description;  // human-friendly description (logs, debug)

    ValidationResult(String value, String description) {
        this.value = value;
        this.description = description;
    }

    /**
     * Value used when serialized to JSON.
     * Keeps output stable regardless of enum name changes.
     */
    @JsonValue
    public String getValue() {
        return value;
    }

    /**
     * Human-friendly description (useful for logs/debug, not required in API).
     */
    public String getDescription() {
        return description;
    }

    /**
     * Parse from string value.
     *
     * Supports:
     * - "satisfied" / "not_satisfied" / "undetermined"
     * - "SATISFIED" / "NOT_SATISFIED" / "UNDETERMINED"
     *
     * Production-safe choice:
     * - Returns UNDETERMINED for null/blank/unknown instead of throwing.
     */
    @JsonCreator
    public static ValidationResult fromString(String value) {
        if (value == null || value.isBlank()) return UNDETERMINED;

        String v = value.trim();
        for (ValidationResult r : values()) {
            if (r.value.equalsIgnoreCase(v) || r.name().equalsIgnoreCase(v)) {
                return r;
            }
        }
        return UNDETERMINED;
    }

    /**
     * Check if this result is satisfied.
     */
    public boolean isSatisfied() {
        return this == SATISFIED;
    }

    /**
     * Check if this result is undetermined (missing data / parsing failure).
     */
    public boolean isUndetermined() {
        return this == UNDETERMINED;
    }

    /**
     * Convert to boolean for backward compatibility.
     *
     * @param treatUndeterminedAs How to treat UNDETERMINED results (true = pass, false = fail)
     * @return boolean result
     */
    public boolean toBoolean(boolean treatUndeterminedAs) {
        switch (this) {
            case SATISFIED:
                return true;
            case NOT_SATISFIED:
                return false;
            case UNDETERMINED:
                return treatUndeterminedAs;
            default:
                return treatUndeterminedAs;
        }
    }

    /**
     * Combine multiple validation results using AND logic.
     *
     * ✅ V3 rules (matches your original Code 1 behavior):
     * - If any result is UNDETERMINED → UNDETERMINED
     * - If none are UNDETERMINED but any are NOT_SATISFIED → NOT_SATISFIED
     * - If all are SATISFIED → SATISFIED
     */
    public static ValidationResult and(ValidationResult... results) {
        if (results == null || results.length == 0) {
            return SATISFIED; // neutral element for AND
        }

        for (ValidationResult result : results) {
            if (result == null || result == UNDETERMINED) {
                return UNDETERMINED;
            }
        }

        for (ValidationResult result : results) {
            if (result == NOT_SATISFIED) {
                return NOT_SATISFIED;
            }
        }

        return SATISFIED;
    }

    /**
     * Convenience pairwise AND.
     */
    public static ValidationResult and(ValidationResult a, ValidationResult b) {
        return and(new ValidationResult[]{a, b});
    }

    /**
     * Combine multiple validation results using OR logic.
     *
     * ✅ V3 rules:
     * - If any result is SATISFIED → SATISFIED
     * - If none are SATISFIED but any are UNDETERMINED → UNDETERMINED
     * - If all are NOT_SATISFIED → NOT_SATISFIED
     */
    public static ValidationResult or(ValidationResult... results) {
        if (results == null || results.length == 0) {
            return NOT_SATISFIED; // neutral element for OR
        }

        boolean hasUndetermined = false;

        for (ValidationResult result : results) {
            if (result == SATISFIED) {
                return SATISFIED;
            }
            if (result == null || result == UNDETERMINED) {
                hasUndetermined = true;
            }
        }

        return hasUndetermined ? UNDETERMINED : NOT_SATISFIED;
    }

    /**
     * Convenience pairwise OR.
     */
    public static ValidationResult or(ValidationResult a, ValidationResult b) {
        return or(new ValidationResult[]{a, b});
    }
}
