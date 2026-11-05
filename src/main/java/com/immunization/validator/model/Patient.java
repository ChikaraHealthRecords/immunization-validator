package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Patient model based on FHIR standard.
 * Contains only the information necessary for immunization validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Patient {
    
    /**
     * Patient identifier (non-PII identifier used for tracking)
     */
    @NotBlank(message = "Patient identifier is required.")
    @JsonProperty("id")
    private String id;
    
    /**
     * Patient's date of birth (used to determine age for requirement validation)
     * Format: YYYY-MM-DD
     * Optional if schoolYear is provided
     */
    @JsonProperty("birthDate")
    private String birthDate;
    
    /**
     * School year/grade identifier (alternative to birthDate for requirement validation)
     * Examples: "Kindergarten", "7th Grade", "12th Grade"
     * Optional if birthDate is provided
     */
    @JsonProperty("schoolYear")
    private String schoolYear;
    
    /**
     * List of immunizations received by the patient
     */
    @Valid
    @JsonProperty("immunization")
    private List<Immunization> immunizations;
    
    /**
     * List of vaccine exceptions/exemptions for this patient.
     * Each exception specifies a vaccine code and the exception type.
     * Example exception types: MEDICAL_CONTRAINDICATION, LABORATORY_EVIDENCE, RELIABLE_HISTORY, RELIGIOUS_EXEMPTION
     */
    @JsonProperty("exceptions")
    private List<VaccineException> exceptions;
}