package com.immunization.validator.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.ZonedDateTime;

/**
 * JPA Entity mapping to the vaccines table.
 *
 * IMPORTANT: vaccineid is the primary key (serial/auto-increment).
 * CVXcode is NOT unique — combo vaccines appear in multiple rows,
 * one per vaccine group they satisfy.
 *
 * Examples of multi-row CVX codes:
 *   CVX 94  (MMRV)          → row for MMR  + row for VARICELLA
 *   CVX 110 (DTaP-Hep B-IPV)→ row for DTAP + row for HepB + row for POLIO
 *   CVX 120 (DTaP-Hib-IPV)  → row for DTAP + row for HIB  + row for POLIO
 *
 * This design means one administered dose can satisfy multiple vaccine
 * group requirements simultaneously — which is clinically correct.
 *
 * @author David Saaka
 * @version 1.0
 */
@Entity
@Table(name = "vaccines")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Vaccine {

    /**
     * Auto-generated primary key (serial in PostgreSQL).
     * NOT the CVX code.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "vaccineid")
    private Integer vaccineId;

    /**
     * CDC CVX code — identifies the vaccine product.
     * Not unique: combo vaccines have one row per group.
     */
    @Column(name = "cvxcode", nullable = false)
    private Integer cvxCode;

    /**
     * Human-readable vaccine name (CDC standard description).
     * e.g. "DTaP", "varicella", "MMR"
     */
    @Column(name = "shortdesc", nullable = false)
    private String shortDesc;

    /**
     * Lifecycle status: "Active", "Inactive", "Non-US", "Never Active"
     * Use "Active" for validation; others are legacy or non-standard.
     */
    @Column(name = "vaccinestatus", nullable = false)
    private String vaccineStatus;

    /**
     * Vaccine group this row belongs to.
     * This is the KEY field for YAML matching.
     *
     * MA-relevant groups:
     *   "DTAP"      → YAML "DTaP"
     *   "TDAP"      → YAML "Tdap"
     *   "POLIO"     → YAML "Polio"
     *   "HepB"      → YAML "HepB"
     *   "MMR"       → YAML "MMR"
     *   "VARICELLA" → YAML "Varicella"
     *   "HIB"       → YAML "Hib"
     *   "MENING"    → YAML "MenACWY"
     */
    @Column(name = "vaccinegroupname", nullable = false)
    private String vaccineGroupName;

    /**
     * CVX code for the vaccine group (group-level identifier).
     * e.g. DTAP group CVX = 107, POLIO group CVX = 89
     */
    @Column(name = "vaccinegroupcvxcode", nullable = false)
    private Integer vaccineGroupCVXcode;

    /**
     * Optional notes field (nullable).
     * Contains additional CDC guidance, usage notes, or warnings.
     */
    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    /**
     * Last updated date — defaults to CURRENT_DATE in DB.
     * Tracks when this vaccine record was last modified in CDC data.
     *
     * Note: Database column is timestamp with time zone
     */
    @Column(name = "lastupdateddate", nullable = false)
    private ZonedDateTime lastUpdatedDate;
}