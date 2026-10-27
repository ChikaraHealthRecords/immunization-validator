package com.immunization.validator;

import com.immunization.validator.model.Vaccine;
import com.immunization.validator.service.FileVaccineCatalog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the CSV-backed vaccine catalog. Plain JUnit: no Spring context, no database.
 */
@DisplayName("FileVaccineCatalog")
class FileVaccineCatalogTest {

    private static final String HEADER =
            "vaccineId,cvxCode,shortDesc,vaccineStatus,vaccineGroupName,vaccineGroupCvxCode,notes\n";

    private static FileVaccineCatalog realCatalog() {
        return new FileVaccineCatalog(new ClassPathResource("vaccines/vaccines.csv"));
    }

    private static FileVaccineCatalog fromText(String csv) {
        return new FileVaccineCatalog(new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)));
    }

    private static List<String> groups(List<Vaccine> vaccines) {
        return vaccines.stream().map(Vaccine::getVaccineGroupName).sorted().collect(Collectors.toList());
    }

    @Test
    @DisplayName("loads every row that vaccines.sql inserts")
    void csvHasSameRowCountAsSql() throws IOException {
        Path sql = Path.of("database", "init-scripts", "vaccines.sql");
        Pattern row = Pattern.compile("^\\s*(Values\\s*)?\\(\\d+,", Pattern.CASE_INSENSITIVE);
        long sqlRows = Files.readAllLines(sql, StandardCharsets.UTF_8).stream()
                .filter(l -> row.matcher(l).find())
                .count();

        assertEquals(sqlRows, realCatalog().findAll().size(),
                "vaccines.csv and vaccines.sql should list the same vaccines");
        assertTrue(sqlRows > 200, "sanity check: the SQL file should have a few hundred rows");
    }

    @Test
    @DisplayName("combo vaccine CVX 110 maps to DTAP, HepB and POLIO")
    void comboVaccineReturnsEveryGroup() {
        assertEquals(List.of("DTAP", "HepB", "POLIO"), groups(realCatalog().findByCvxCode(110)));
    }

    @Test
    @DisplayName("MMRV CVX 94 maps to MMR and VARICELLA")
    void mmrvReturnsTwoGroups() {
        assertEquals(List.of("MMR", "VARICELLA"), groups(realCatalog().findByCvxCode(94)));
    }

    @Test
    @DisplayName("unknown and null CVX codes return an empty list")
    void unknownCodeIsEmpty() {
        FileVaccineCatalog catalog = realCatalog();
        assertTrue(catalog.findByCvxCode(999999).isEmpty());
        assertTrue(catalog.findByCvxCode(null).isEmpty());
    }

    @Test
    @DisplayName("group lookup ignores case")
    void groupLookupIgnoresCase() {
        FileVaccineCatalog catalog = realCatalog();
        List<Vaccine> lower = catalog.findByGroupName("dtap");
        assertFalse(lower.isEmpty());
        assertEquals(lower.size(), catalog.findByGroupName("DTAP").size());
        assertTrue(catalog.findByGroupName(null).isEmpty());
    }

    @Test
    @DisplayName("a description with commas inside quotes stays in one column")
    void quotedCommasSurvive() {
        Vaccine td = realCatalog().findByCvxCode(9).get(0);
        assertTrue(td.getShortDesc().contains(", 2 Lf"), td.getShortDesc());
        assertEquals("Td", td.getVaccineGroupName());
    }

    @Test
    @DisplayName("quotes, blank lines, CRLF and a missing final newline are handled")
    void csvEdgeCases() {
        String csv = HEADER.replace("\n", "\r\n")
                + "1,3,\"MMR, \"\"special\"\"\",Active,MMR,3,\"note\r\nline two\"\r\n"
                + "\r\n"
                + "2,8,Hep B,Active,HepB,45,";
        FileVaccineCatalog catalog = fromText(csv);

        assertEquals(2, catalog.findAll().size());
        assertEquals("MMR, \"special\"", catalog.findByCvxCode(3).get(0).getShortDesc());
        assertEquals("note\r\nline two", catalog.findByCvxCode(3).get(0).getNotes());
        assertNull(catalog.findByCvxCode(8).get(0).getNotes());
    }

    @Test
    @DisplayName("a wrong header stops startup")
    void wrongHeaderFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> fromText("id,cvx\n1,3\n"));
        assertTrue(e.getMessage().contains("wrong header"), e.getMessage());
    }

    @Test
    @DisplayName("a non-numeric CVX code stops startup and names the record")
    void badNumberFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> fromText(HEADER + "1,abc,MMR,Active,MMR,3,\n"));
        assertTrue(e.getMessage().contains("cvxCode") && e.getMessage().contains("record 2"), e.getMessage());
    }

    @Test
    @DisplayName("a missing group name stops startup")
    void missingGroupFails() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> fromText(HEADER + "1,3,MMR,Active,,3,\n"));
        assertTrue(e.getMessage().contains("vaccineGroupName"), e.getMessage());
    }

    @Test
    @DisplayName("a row with the wrong number of columns stops startup")
    void wrongColumnCountFails() {
        assertThrows(IllegalStateException.class,
                () -> fromText(HEADER + "1,3,MMR,Active\n"));
    }

    @Test
    @DisplayName("an empty file and a header-only file stop startup")
    void emptyFilesFail() {
        assertThrows(IllegalStateException.class, () -> fromText(""));
        assertThrows(IllegalStateException.class, () -> fromText(HEADER));
    }

    @Test
    @DisplayName("an unclosed quote stops startup")
    void unclosedQuoteFails() {
        assertThrows(IllegalStateException.class,
                () -> fromText(HEADER + "1,3,\"MMR,Active,MMR,3,\n"));
    }

    @Test
    @DisplayName("a missing file stops startup")
    void missingFileFails() {
        Resource missing = new ClassPathResource("vaccines/does-not-exist.csv");
        assertThrows(IllegalStateException.class, () -> new FileVaccineCatalog(missing));
    }
}
