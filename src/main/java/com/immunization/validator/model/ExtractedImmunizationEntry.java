package com.immunization.validator.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * A single immunization row from the JSON schema submitted to LlamaExtract.
 *
 * One entry per (vaccine family, date) pair as rendered in the source
 * document -- combo-vaccine table rows commonly repeat the same family
 * label and date across columns, which is exactly the duplicate input
 * {@link com.immunization.validator.service.ValidationService#deduplicateImmunizations}
 * is designed to collapse.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtractedImmunizationEntry {

    /** Raw label as it appears in the document, e.g. "DTAP/DT", "Hib", "Pneumopcv". */
    @JsonProperty("vaccineFamily")
    private String vaccineFamily;

    /** Requested from LlamaExtract as ISO 8601 (YYYY-MM-DD); may still arrive in the document's native format. */
    @JsonProperty("administeredDate")
    private String administeredDate;
}
