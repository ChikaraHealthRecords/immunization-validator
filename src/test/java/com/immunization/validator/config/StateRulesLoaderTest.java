package com.immunization.validator.config;

import com.immunization.validator.model.StateRequirements;
import com.immunization.validator.model.ValidationRequirement;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class StateRulesLoaderTest {

    private final StateRulesLoader loader = new StateRulesLoader();

    // ─── The real rule files ────────────────────────────────────────────────

    @Test
    @DisplayName("Massachusetts loads from rules/states/ma.yml with all five school-year groups")
    void loadsMassachusetts() {
        Map<String, StateRuleFile> files = loader.load(StateRulesLoader.DEFAULT_LOCATION);
        StateRequirements ma = RequirementsConfiguration.toStateRequirements(files.get("MA"));

        assertEquals("Massachusetts", ma.getName());
        assertEquals("105 CMR 220.000", ma.getAuthority());
        assertEquals("March 2025", ma.getRulesPublished());
        assertEquals(Set.of("preschool", "K-6", "7-10", "11-12", "college"), ma.getRequirementsBySchoolYear().keySet());
        assertEquals(28, ma.getRequirementsBySchoolYear().values().stream().mapToInt(List::size).sum());

        ValidationRequirement dtap = ma.getRequirementsBySchoolYear().get("K-6").stream()
                .filter(r -> r.getVaccineCode().equals("DTaP")).findFirst().orElseThrow();
        assertEquals(5, dtap.getMinDoses());
        assertEquals(List.of("4th dose on or after 4th birthday"), dtap.getAlternateRequirements().get(0).getDateConditions());
    }

    @Test
    @DisplayName("Every rule file in the repo is valid and named after its state")
    void allRepoFilesAreValid() {
        Map<String, StateRuleFile> files = loader.load(StateRulesLoader.DEFAULT_LOCATION);
        assertFalse(files.isEmpty());
        files.forEach((code, file) -> assertEquals(code, file.getState()));
    }

    // ─── Mistakes contributors might make ───────────────────────────────────

    private static final String VALID = """
            state: RI
            name: Rhode Island
            rulesPublished: July 2025
            schoolYear:
              - schoolYear: "K"
                vaccineCode: "MMR"
                minDoses: 2
            """;

    private static String write(Path dir, String name, String yaml) throws IOException {
        Files.writeString(dir.resolve(name), yaml);
        return dir.toUri() + "*.yml";
    }

    private static String failureFor(Path dir, String name, String yaml) throws IOException {
        String location = write(dir, name, yaml);
        return assertThrows(IllegalStateException.class, () -> new StateRulesLoader().load(location)).getMessage();
    }

    @Test
    void loadsFromAnExternalFolder(@TempDir Path dir) throws IOException {
        Map<String, StateRuleFile> files = loader.load(write(dir, "ri.yml", VALID));
        assertEquals(Set.of("RI"), files.keySet());
    }

    @Test
    void rejectsUnknownKeysWithTheLine(@TempDir Path dir) throws IOException {
        String message = failureFor(dir, "ri.yml", VALID.replace("minDoses: 2", "minDose: 2"));
        assertTrue(message.contains("ri.yml"), message);
        assertTrue(message.contains("minDose"), message);
        assertTrue(message.contains("unknown key 'minDose' (line 7)"), message);
        assertTrue(message.contains("minDoses"), message); // suggests the right key
    }

    @Test
    void rejectsAFileNameThatDoesNotMatchTheState(@TempDir Path dir) throws IOException {
        String message = failureFor(dir, "rhode-island.yml", VALID);
        assertTrue(message.contains("name the file ri.yml"), message);
    }

    @Test
    void rejectsTheSameStateTwice(@TempDir Path dir) throws IOException {
        // e.g. a copy left in a second rules folder that's also on the pattern
        write(Files.createDirectory(dir.resolve("a")), "ri.yml", VALID);
        write(Files.createDirectory(dir.resolve("b")), "ri.yml", VALID);
        String location = dir.toUri() + "**/*.yml";
        String message = assertThrows(IllegalStateException.class, () -> loader.load(location)).getMessage();
        assertTrue(message.contains("defined twice"), message);
    }

    @Test
    void rejectsMissingRequiredFields(@TempDir Path dir) throws IOException {
        String message = failureFor(dir, "ri.yml", """
                state: RI
                schoolYear:
                  - vaccineCode: ""
                    minDoses: -1
                """);
        assertTrue(message.contains("'name' is required"), message);
        assertTrue(message.contains("'rulesPublished' is required"), message);
        assertTrue(message.contains("'vaccineCode' is required"), message);
        assertTrue(message.contains("'minDoses' must be 0 or more"), message);
        assertTrue(message.contains("'schoolYear' is required"), message);
    }

    @Test
    void rejectsAFileWithNoRequirements(@TempDir Path dir) throws IOException {
        String message = failureFor(dir, "ri.yml", "state: RI\nname: Rhode Island\nrulesPublished: July 2025\n");
        assertTrue(message.contains("add requirements"), message);
    }

    @Test
    void rejectsLowercaseOrMissingStateCodes(@TempDir Path dir) throws IOException {
        assertTrue(failureFor(dir, "ri.yml", VALID.replace("state: RI", "state: ri")).contains("two-letter code"));
    }

    @Test
    void failsWhenNoRuleFilesAreFound(@TempDir Path dir) {
        String location = dir.toUri() + "*.yml";
        String message = assertThrows(IllegalStateException.class, () -> loader.load(location)).getMessage();
        assertTrue(message.contains("No state rule files found"), message);
    }
}
