package com.immunization.validator.model;

/**
 * Represents the result of a validation check.
 * 
 * Using a tri-state pattern allows us to distinguish between:
 * - Conditions that are definitely met (SATISFIED)
 * - Conditions that are definitely not met (NOT_SATISFIED)
 * - Conditions that cannot be evaluated (UNDETERMINED)
 * 
 * @author Saakad
 * @version 3.0
 */
public enum ValidationResult {
    /**
     * The validation condition is satisfied.
     * Example: Patient has 4 DTaP doses with 4th on 4th birthday
     */
    SATISFIED("Condition satisfied"),
    
    /**
     * The validation condition is not satisfied.
     * Example: Patient has 3 DTaP doses but needs 4
     */
    NOT_SATISFIED("Condition not satisfied"),
    
    /**
     * The validation condition cannot be evaluated.
     * Example: Date condition syntax is unparseable, or required data is missing
     */
    UNDETERMINED("Condition cannot be determined");
    
    private final String description;
    
    ValidationResult(String description) {
        this.description = description;
    }
    
    public String getDescription() {
        return description;
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
     * Rules:
     * - If any result is NOT_SATISFIED → NOT_SATISFIED
     * - If all are SATISFIED → SATISFIED
     * - If any is UNDETERMINED (and none are NOT_SATISFIED) → UNDETERMINED
     */
    public static ValidationResult and(ValidationResult... results) {
        boolean hasUndetermined = false;
        
        for (ValidationResult result : results) {
            if (result == NOT_SATISFIED) {
                return NOT_SATISFIED;
            }
            if (result == UNDETERMINED) {
                hasUndetermined = true;
            }
        }
        
        return hasUndetermined ? UNDETERMINED : SATISFIED;
    }
    
    /**
     * Combine multiple validation results using OR logic.
     * 
     * Rules:
     * - If any result is SATISFIED → SATISFIED
     * - If all are NOT_SATISFIED → NOT_SATISFIED
     * - If any is UNDETERMINED (and none are SATISFIED) → UNDETERMINED
     */
    public static ValidationResult or(ValidationResult... results) {
        boolean hasUndetermined = false;
        
        for (ValidationResult result : results) {
            if (result == SATISFIED) {
                return SATISFIED;
            }
            if (result == UNDETERMINED) {
                hasUndetermined = true;
            }
        }
        
        return hasUndetermined ? UNDETERMINED : NOT_SATISFIED;
    }
}