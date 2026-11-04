package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request model for batch validation of multiple patients.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BatchValidationRequest {
    
    /**
     * State code to validate against
     */
    @NotBlank(message = "Please select a state.")
    @JsonProperty("state")
    private String state;
    
    /**
     * Age in years to validate against (or school year identifier)
     */
    @JsonProperty("age")
    private Integer age;
    
    /**
     * School year identifier (alternative to age)
     */
    @JsonProperty("schoolYear")
    private String schoolYear;
    
    /**
     * Response mode: "simple" or "detailed"
     */
    @NotBlank(message = "Response mode is required.")
    @JsonProperty("responseMode")
    private String responseMode;
    
    /**
     * List of patients to validate
     */
    @NotNull(message = "At least one patient record is required.")
    @Size(min = 1, message = "At least one patient record is required.")
    @JsonProperty("patients")
    private List<Patient> patients;
}

