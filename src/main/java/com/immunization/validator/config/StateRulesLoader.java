package com.immunization.validator.config;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.immunization.validator.config.RequirementsConfiguration.AlternateRequirementConfig;
import com.immunization.validator.config.RequirementsConfiguration.RequirementConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Loads one rule file per state from a resource pattern, by default
 * {@code classpath*:rules/states/*.yml}. Adding a state means adding a file;
 * nothing else needs to list it.
 *
 * Files are parsed strictly so mistakes fail at startup with the file name
 * (and, for parse errors, the line) instead of silently weakening a rule:
 * unknown keys such as a misspelt {@code minDose}, a file name that doesn't
 * match its state code, duplicate states, or missing required fields.
 */
@Slf4j
public class StateRulesLoader {

    public static final String DEFAULT_LOCATION = "classpath*:rules/states/*.yml";

    private static final Pattern STATE_CODE = Pattern.compile("[A-Z]{2}");

    private final ObjectMapper yaml = new ObjectMapper(new YAMLFactory())
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES);

    private final ResourcePatternResolver resolver;

    public StateRulesLoader() {
        this(new PathMatchingResourcePatternResolver());
    }

    public StateRulesLoader(ResourcePatternResolver resolver) {
        this.resolver = resolver;
    }

    /**
     * @param location a resource pattern such as {@code classpath*:rules/states/*.yml}
     *                 or {@code file:/etc/validator/rules/*.yml}
     * @return rule files keyed by state code, in file-name order
     * @throws IllegalStateException if no files are found or any file is invalid
     */
    public Map<String, StateRuleFile> load(String location) {
        Resource[] resources;
        try {
            resources = resolver.getResources(location);
        } catch (IOException e) {
            throw new IllegalStateException("Could not list state rule files at " + location, e);
        }

        List<Resource> files = Arrays.stream(resources)
                .filter(Resource::exists)
                .sorted(Comparator.comparing(r -> String.valueOf(r.getFilename())))
                .toList();
        if (files.isEmpty()) {
            throw new IllegalStateException("No state rule files found at " + location
                    + ". Without them every validation would come back undetermined.");
        }

        Map<String, StateRuleFile> byState = new LinkedHashMap<>();
        Map<String, String> fileForState = new LinkedHashMap<>();
        for (Resource resource : files) {
            String fileName = resource.getFilename();
            StateRuleFile rules = parse(resource, fileName);
            validate(rules, fileName);

            String previous = fileForState.putIfAbsent(rules.getState(), fileName);
            if (previous != null) {
                throw new IllegalStateException("State " + rules.getState() + " is defined twice: in "
                        + previous + " and " + fileName);
            }
            byState.put(rules.getState(), rules);
            log.info("Loaded rules for {} from {} (published {})", rules.getState(), fileName, rules.getRulesPublished());
        }
        return byState;
    }

    private StateRuleFile parse(Resource resource, String fileName) {
        try (InputStream in = resource.getInputStream()) {
            StateRuleFile rules = yaml.readValue(in, StateRuleFile.class);
            if (rules == null) {
                throw new IllegalStateException(fileName + " is empty");
            }
            return rules;
        } catch (UnrecognizedPropertyException e) {
            // Usually a typo, e.g. "minDose" for "minDoses".
            throw new IllegalStateException("Invalid state rule file " + fileName + ": unknown key '"
                    + e.getPropertyName() + "'" + atLine(e) + ". Allowed keys here: "
                    + e.getKnownPropertyIds().stream().map(String::valueOf).sorted().toList(), e);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid state rule file " + fileName + ": " + e.getOriginalMessage()
                    + atLine(e), e);
        } catch (IOException e) {
            throw new IllegalStateException("Could not read state rule file " + fileName, e);
        }
    }

    private void validate(StateRuleFile rules, String fileName) {
        List<String> problems = new ArrayList<>();

        String state = rules.getState();
        String expected = fileName == null ? null : fileName.replaceFirst("\\.ya?ml$", "").toUpperCase(Locale.ROOT);
        if (state == null || !STATE_CODE.matcher(state).matches()) {
            problems.add("'state' must be a two-letter code in capitals, e.g. MA");
        } else if (!state.equals(expected)) {
            problems.add("'state' is " + state + " but the file name says " + expected
                    + "; name the file " + state.toLowerCase(Locale.ROOT) + ".yml");
        }
        if (isBlank(rules.getName())) problems.add("'name' is required, e.g. Massachusetts");
        if (isBlank(rules.getRulesPublished())) {
            problems.add("'rulesPublished' is required: the date the state published these rules");
        }

        boolean hasSchoolYear = rules.getSchoolYear() != null && !rules.getSchoolYear().isEmpty();
        boolean hasAge = rules.getAge() != null && !rules.getAge().isEmpty();
        if (!hasSchoolYear && !hasAge) {
            problems.add("add requirements under 'schoolYear' or 'age'");
        }
        checkRequirements(rules.getSchoolYear(), "schoolYear", problems);
        checkRequirements(rules.getAge(), "age", problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException("Invalid state rule file " + fileName + ":\n  - "
                    + String.join("\n  - ", problems));
        }
    }

    private void checkRequirements(List<RequirementConfig> requirements, String group, List<String> problems) {
        if (requirements == null) return;
        for (int i = 0; i < requirements.size(); i++) {
            RequirementConfig r = requirements.get(i);
            String where = group + "[" + i + "]" + (r != null && !isBlank(r.getVaccineCode()) ? " (" + r.getVaccineCode() + ")" : "");
            if (r == null) {
                problems.add(where + " is empty");
                continue;
            }
            if (isBlank(r.getVaccineCode())) problems.add(where + ": 'vaccineCode' is required");
            if (r.getMinDoses() == null || r.getMinDoses() < 0) problems.add(where + ": 'minDoses' must be 0 or more");
            if (group.equals("schoolYear") && isBlank(r.getSchoolYear())) problems.add(where + ": 'schoolYear' is required");
            if (group.equals("age") && (r.getAge() == null || r.getAge() < 0)) problems.add(where + ": 'age' must be 0 or more");

            List<AlternateRequirementConfig> alternates = r.getAlternateRequirements();
            if (alternates != null) {
                for (int j = 0; j < alternates.size(); j++) {
                    AlternateRequirementConfig a = alternates.get(j);
                    if (a == null || a.getMinDoses() == null || a.getMinDoses() < 0) {
                        problems.add(where + ".alternateRequirements[" + j + "]: 'minDoses' must be 0 or more");
                    }
                }
            }
        }
    }

    private static String atLine(JsonProcessingException e) {
        return e.getLocation() != null ? " (line " + e.getLocation().getLineNr() + ")" : "";
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
