package com.immunization.validator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Maps a raw vaccine family label as it appears in LlamaExtract output
 * (e.g. "DTAP/DT", "Pneumopcv") to the canonical vaccine group name used
 * by RequirementsService / application.yml (e.g. "DTaP", "PneumoPCV").
 *
 * Distinct from {@link Vaccine}, which maps numeric CVX codes to groups.
 */
@Entity
@Table(name = "vaccine_family_aliases")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VaccineFamilyAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Integer id;

    @Column(name = "raw_label", nullable = false, unique = true)
    private String rawLabel;

    @Column(name = "canonical_group", nullable = false)
    private String canonicalGroup;
}
