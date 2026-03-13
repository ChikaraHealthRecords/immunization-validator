package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Metadata about a validation run.
 * Attached to ValidationResponse to support auditing and debugging.
 *
 * @version 3.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationMetadata {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime validatedAt;

    /** State validated against */
    private String state;

    /** School year validated against */
    private String schoolYear;

    /** Total number of requirements evaluated */
    private Integer totalRequirements;

    /** How many requirements were satisfied */
    private Integer satisfiedRequirements;

    /** How many requirements were not satisfied */
    private Integer unsatisfiedRequirements;

    /** How many requirements could not be evaluated */
    private Integer undeterminedRequirements;

    /** Version of the validator that produced this result */
    private String validatorVersion;
}