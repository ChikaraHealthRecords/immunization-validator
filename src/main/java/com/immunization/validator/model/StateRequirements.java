package com.immunization.validator.model;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * Represents immunization requirements for a specific state.
 *
 * Requirements can be organized by:
 * - Age (e.g., 2 years, 5 years, 12 years)
 * - School year/grade (e.g., "Kindergarten", "7th Grade", "11th Grade")
 *
 * This class is typically populated from YAML configuration files.
 *
 * @author Saakad
 * @since 2026-01-01
 * @version 2.0 - Added support for school year-based requirements
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StateRequirements {

    /**
     * State code (e.g., "CA", "MA", "NY", "TX")
     */
    private String stateCode;

    /**
     * Full state name (e.g., "Massachusetts", "California")
     * Optional - used for display purposes
     */
    private String name;

    /** Who publishes the rules, e.g. "MA DPH – Immunization Division". */
    private String source;

    /** Legal citation, e.g. "105 CMR 220.000". */
    private String authority;

    /** When the state published these rules, e.g. "March 2025". */
    private String rulesPublished;

    /**
     * Map of age groups (in years) to their required immunizations.
     *
     * Key: Age in years (e.g., 2, 5, 12, 18)
     * Value: List of validation requirements for that age
     *
     * Example:
     * - Age 2: Preschool requirements (Hib, DTaP, Polio, HepB, MMR, Varicella)
     * - Age 5: Kindergarten requirements (DTaP 5 doses, MMR 2 doses, etc.)
     * - Age 12: Grade 7 requirements (Tdap, MenACWY, etc.)
     */
    private Map<Integer, List<ValidationRequirement>> requirementsByAge;

    /**
     * Map of school year/grades to their required immunizations.
     *
     * Key: School year identifier (e.g., "Kindergarten", "7th Grade", "11th Grade")
     * Value: List of validation requirements for that school year
     *
     * Example:
     * - "Kindergarten": Same as age 5 requirements
     * - "7th Grade": Tdap, MenACWY (1 dose after 10th birthday)
     * - "11th Grade": MenACWY (2 doses, 2nd after 16th birthday)
     *
     * This provides an alternative to age-based requirements when grade level
     * is known but exact age is not.
     */
    private Map<String, List<ValidationRequirement>> requirementsBySchoolYear;

    /**
     * General notes or information about this state's requirements.
     * Example: "Requirements based on 105 CMR 220.000"
     */
    private String notes;
}