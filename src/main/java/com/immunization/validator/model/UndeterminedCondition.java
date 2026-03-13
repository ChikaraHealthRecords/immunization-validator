package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Describes a requirement that could not be evaluated due to missing data.
 * Included in ValidationResponse when status is UNDETERMINED.
 *
 * @version 3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UndeterminedCondition {

    /** Vaccine code affected (e.g. "MMR", "DTaP") */
    private String vaccineCode;

    /** The condition expression that could not be evaluated */
    private String condition;

    /** Why the condition could not be evaluated */
    private String reason;

    /** Additional context */
    private String details;

    /** What the caller can provide to allow evaluation */
    private String suggestion;
}