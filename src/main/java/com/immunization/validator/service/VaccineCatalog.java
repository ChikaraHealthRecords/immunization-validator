package com.immunization.validator.service;

import com.immunization.validator.model.Vaccine;

import java.util.List;

/**
 * Read-only source of CVX vaccine data.
 *
 * Two implementations:
 *  - FileVaccineCatalog     (default)  loads vaccines.csv once at startup, no database needed.
 *  - DatabaseVaccineCatalog (optional) reads the vaccines table through JPA.
 *
 * Choose with the property immunization.vaccines.source = file | database.
 *
 * One CVX code can return several rows: a combo vaccine has one row per vaccine group
 * it satisfies (CVX 110 returns DTAP, HepB and POLIO).
 */
public interface VaccineCatalog {

    /** @return every row for this CVX code, or an empty list when the code is null or unknown */
    List<Vaccine> findByCvxCode(Integer cvxCode);

    /** @return every row for this vaccine group (case-insensitive), or an empty list */
    List<Vaccine> findByGroupName(String groupName);

    /** @return all rows */
    List<Vaccine> findAll();
}
