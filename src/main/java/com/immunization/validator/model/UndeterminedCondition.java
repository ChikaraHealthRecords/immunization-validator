package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

/**
 * Represents a validation condition that could not be evaluated.
 *
 * @author Saakad
 * @version 3.0
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UndeterminedCondition {

    /**
     * The vaccine code this condition applies to
     */
    private String vaccineCode;

    /**
     * The condition that could not be evaluated
     */
    private String condition;

    /**
     * Reason why the condition is undetermined
     */
    private String reason;

    /**
     * Detailed error information
     */
    private String details;

    /**
     * Suggested action to resolve
     */
    private String suggestion;
}