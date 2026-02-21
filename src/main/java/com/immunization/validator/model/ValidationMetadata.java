package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Metadata about the validation process.
 * 
 * @author Saakad
 * @version 3.0
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ValidationMetadata {
    
    /**
     * Timestamp when validation was performed
     */
    private LocalDateTime validatedAt;
    
    /**
     * State code used for validation
     */
    private String state;
    
    /**
     * School year used for validation
     */
    private String schoolYear;
    
    /**
     * Total number of requirements checked
     */
    private Integer totalRequirements;
    
    /**
     * Number of requirements satisfied
     */
    private Integer satisfiedRequirements;
    
    /**
     * Number of requirements not satisfied
     */
    private Integer unsatisfiedRequirements;
    
    /**
     * Number of requirements that could not be determined
     */
    private Integer undeterminedRequirements;
    
    /**
     * Validator version
     */
    private String validatorVersion;
}