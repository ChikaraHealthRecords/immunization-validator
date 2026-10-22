package com.immunization.validator.config;

import com.immunization.validator.config.RequirementsConfiguration.RequirementConfig;
import lombok.Data;

import java.util.List;

/**
 * One state's rule file, e.g. {@code rules/states/ma.yml}. See CONTRIBUTING.md
 * ("How state rules are structured") for the format.
 */
@Data
public class StateRuleFile {

    /** Two-letter state code. Must match the file name (ma.yml -> MA). */
    private String state;

    /** Display name, e.g. "Massachusetts". */
    private String name;

    /** Who publishes the rules, e.g. "MA DPH – Immunization Division". */
    private String source;

    /** Legal citation, e.g. "105 CMR 220.000". */
    private String authority;

    /**
     * When the state published the rules encoded in the file (the date on its
     * own requirements document), e.g. "March 2025". Shown to users as
     * "from rules published ...".
     */
    private String rulesPublished;

    private String notes;

    /** Requirements grouped by the state's school-year names. */
    private List<RequirementConfig> schoolYear;

    /** Requirements by age in years, for states that define them that way. */
    private List<RequirementConfig> age;
}
