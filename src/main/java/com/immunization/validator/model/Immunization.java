package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Immunization model based on FHIR standard.
 * Represents a single immunization record.
 *
 * Version 2.0: Added CVX code support for CDC-standardized vaccine identification
 *
 * Supports both traditional vaccine codes and CDC CVX codes:
 * - vaccineCode: Traditional vaccine name (e.g., "DTaP", "MMR", "HepB")
 * - cvxCode: CDC standardized numeric code (e.g., 110 for Pediarix, 94 for MMRV)
 *
 * Either vaccineCode OR cvxCode must be provided (or both).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Immunization {

    /**
     * Vaccine code (e.g., "DTaP", "MMR", "HepB")
     * Uses standard vaccine codes where applicable
     *
     * Either vaccineCode OR cvxCode must be provided
     */
    @JsonProperty("vaccineCode")
    private String vaccineCode;

    /**
     * CDC CVX code - standardized numeric vaccine product identifier
     *
     * Common CVX codes:
     * - 110 = DTaP-HepB-IPV (Pediarix combo)
     * - 94 = MMRV (ProQuad combo)
     * - 20 = DTaP
     * - 10 = Polio (IPV)
     * - 8 = HepB
     * - 3 = MMR
     * - 21 = Varicella
     * - 17 = Hib
     *
     * CVX codes are translated to vaccineCode via database lookup
     * Combo vaccines (like CVX 110) expand to multiple vaccine groups
     *
     * Either vaccineCode OR cvxCode must be provided
     */
    @JsonProperty("cvxCode")
    private Integer cvxCode;

    /**
     * Date when the immunization was administered
     * Format: YYYY-MM-DD
     */
    @NotBlank(message = "Administration date is required")
    @JsonProperty("occurrenceDateTime")
    private String occurrenceDateTime;

    /**
     * Number of doses (for vaccines requiring multiple doses)
     * Optional, defaults to 1 if not specified
     */
    @JsonProperty("doseNumber")
    private Integer doseNumber;

    /**
     * Vaccine lot number. When present, indicates a verified/administered dose
     * rather than a patient-reported one. Used during de-duplication: if two
     * records share the same vaccineCode and date, the one with a lot number
     * is preferred.
     */
    @JsonProperty("lotNumber")
    private String lotNumber;
}