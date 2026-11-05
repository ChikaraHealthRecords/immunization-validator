package com.immunization.validator.config;

import com.immunization.validator.model.AlternateRequirement;
import com.immunization.validator.model.StateRequirements;
import com.immunization.validator.model.ValidationRequirement;
import com.immunization.validator.service.RequirementsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads immunization requirements, one rule file per state, and exposes them
 * as the {@link RequirementsService} bean.
 *
 * Files live in {@code src/main/resources/rules/states/} (e.g. {@code ma.yml}).
 * Set {@code immunization.requirements.location} to load them from elsewhere,
 * e.g. {@code file:/etc/validator/rules/*.yml}.
 */
@Slf4j
@Configuration
public class RequirementsConfiguration {

    @Value("${immunization.requirements.location:" + StateRulesLoader.DEFAULT_LOCATION + "}")
    private String location;

    @Bean
    public RequirementsService requirementsService() {
        Map<String, StateRequirements> requirementsMap = new HashMap<>();
        new StateRulesLoader().load(location).forEach((stateCode, file) ->
                requirementsMap.put(stateCode, toStateRequirements(file)));

        log.info("Loaded immunization requirements for {} states: {}", requirementsMap.size(), requirementsMap.keySet());
        return new RequirementsService(requirementsMap);
    }

    /** Builds the model the validator uses from one state's rule file. */
    public static StateRequirements toStateRequirements(StateRuleFile file) {
        StateRequirements stateRequirements = StateRequirements.builder()
                .stateCode(file.getState())
                .name(file.getName())
                .source(file.getSource())
                .authority(file.getAuthority())
                .rulesPublished(file.getRulesPublished())
                .notes(file.getNotes())
                .requirementsByAge(new HashMap<>())
                .requirementsBySchoolYear(new HashMap<>())
                .build();

        // Process age-based requirements
        if (file.getAge() != null) {
            Map<Integer, List<ValidationRequirement>> requirementsByAge = new HashMap<>();
            for (RequirementConfig config : file.getAge()) {
                int age = config.getAge();
                requirementsByAge.putIfAbsent(age, new ArrayList<>());
                requirementsByAge.get(age).add(mapToValidationRequirement(config));
            }
            stateRequirements.setRequirementsByAge(requirementsByAge);
        }

        // Process school year-based requirements
        if (file.getSchoolYear() != null) {
            Map<String, List<ValidationRequirement>> requirementsBySchoolYear = new HashMap<>();
            for (RequirementConfig config : file.getSchoolYear()) {
                String schoolYear = config.getSchoolYear();
                requirementsBySchoolYear.putIfAbsent(schoolYear, new ArrayList<>());
                requirementsBySchoolYear.get(schoolYear).add(mapToValidationRequirement(config));
            }
            stateRequirements.setRequirementsBySchoolYear(requirementsBySchoolYear);
        }

        return stateRequirements;
    }

    private static ValidationRequirement mapToValidationRequirement(RequirementConfig config) {
        List<AlternateRequirement> alternateRequirements = null;
        if (config.getAlternateRequirements() != null && !config.getAlternateRequirements().isEmpty()) {
            alternateRequirements = config.getAlternateRequirements().stream()
                    .map(alt -> AlternateRequirement.builder()
                            .alternateVaccineCode(alt.getAlternateVaccineCode())
                            .minDoses(alt.getMinDoses())
                            .description(alt.getDescription())
                            .condition(alt.getCondition())
                            .dateConditions(alt.getDateConditions())         // ✅ FIXED: Added
                            .intervalConditions(alt.getIntervalConditions()) // ✅ FIXED: Added
                            .notes(alt.getNotes())                           // ✅ FIXED: Added
                            .build())
                    .collect(Collectors.toList());
        }

        return ValidationRequirement.builder()
                .vaccineCode(config.getVaccineCode())
                .minDoses(config.getMinDoses())
                // ❌ REMOVED: .minAge(config.getMinAge())      - Field doesn't exist
                // ❌ REMOVED: .maxAge(config.getMaxAge())      - Field doesn't exist
                .description(config.getDescription())
                .dateConditions(config.getDateConditions())           // ✅ FIXED: Added
                .intervalConditions(config.getIntervalConditions())   // ✅ FIXED: Added
                .alternateRequirements(alternateRequirements)
                .acceptedExceptions(config.getAcceptedExceptions())
                .notes(config.getNotes())                             // ✅ FIXED: Added
                .build();
    }

    /**
     * One requirement in a state rule file (an entry under schoolYear or age).
     */
    public static class RequirementConfig {
        private String vaccineCode;
        private Integer minDoses;
        // ❌ REMOVED: private Integer minAge;       - Not used in ValidationRequirement
        // ❌ REMOVED: private Integer maxAge;       - Not used in ValidationRequirement
        private String description;
        private Integer age;
        private String schoolYear;
        private List<String> dateConditions;           // ✅ FIXED: Added
        private List<String> intervalConditions;       // ✅ FIXED: Added
        private List<AlternateRequirementConfig> alternateRequirements;
        private List<String> acceptedExceptions;
        private String notes;                          // ✅ FIXED: Added

        // Getters and setters
        public String getVaccineCode() { return vaccineCode; }
        public void setVaccineCode(String vaccineCode) { this.vaccineCode = vaccineCode; }

        public Integer getMinDoses() { return minDoses; }
        public void setMinDoses(Integer minDoses) { this.minDoses = minDoses; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public Integer getAge() { return age; }
        public void setAge(Integer age) { this.age = age; }

        public String getSchoolYear() { return schoolYear; }
        public void setSchoolYear(String schoolYear) { this.schoolYear = schoolYear; }

        public List<String> getDateConditions() { return dateConditions; }
        public void setDateConditions(List<String> dateConditions) {
            this.dateConditions = dateConditions;
        }

        public List<String> getIntervalConditions() { return intervalConditions; }
        public void setIntervalConditions(List<String> intervalConditions) {
            this.intervalConditions = intervalConditions;
        }

        public List<AlternateRequirementConfig> getAlternateRequirements() {
            return alternateRequirements;
        }
        public void setAlternateRequirements(List<AlternateRequirementConfig> alternateRequirements) {
            this.alternateRequirements = alternateRequirements;
        }

        public List<String> getAcceptedExceptions() { return acceptedExceptions; }
        public void setAcceptedExceptions(List<String> acceptedExceptions) {
            this.acceptedExceptions = acceptedExceptions;
        }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }

    /**
     * Another way to meet a requirement (an entry under alternateRequirements).
     */
    public static class AlternateRequirementConfig {
        private String alternateVaccineCode;
        private Integer minDoses;
        private String description;
        private String condition;
        private List<String> dateConditions;           // ✅ FIXED: Added
        private List<String> intervalConditions;       // ✅ FIXED: Added
        private String notes;                          // ✅ FIXED: Added

        public String getAlternateVaccineCode() { return alternateVaccineCode; }
        public void setAlternateVaccineCode(String alternateVaccineCode) {
            this.alternateVaccineCode = alternateVaccineCode;
        }

        public Integer getMinDoses() { return minDoses; }
        public void setMinDoses(Integer minDoses) { this.minDoses = minDoses; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }

        public List<String> getDateConditions() { return dateConditions; }
        public void setDateConditions(List<String> dateConditions) {
            this.dateConditions = dateConditions;
        }

        public List<String> getIntervalConditions() { return intervalConditions; }
        public void setIntervalConditions(List<String> intervalConditions) {
            this.intervalConditions = intervalConditions;
        }

        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
    }
}