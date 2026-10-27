package com.immunization.validator.service;

import com.immunization.validator.model.Vaccine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Vaccine catalog loaded from a CSV file. No database involved.
 *
 * Active when immunization.vaccines.source is "file" or not set (the default).
 * The file is read once at startup. A bad file stops the application from starting,
 * the same way a bad state rules file does, so a wrong catalog never gets used silently.
 *
 * Expected header (exact, in this order):
 *   vaccineId,cvxCode,shortDesc,vaccineStatus,vaccineGroupName,vaccineGroupCvxCode,notes
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "immunization.vaccines.source", havingValue = "file", matchIfMissing = true)
public class FileVaccineCatalog implements VaccineCatalog {

    static final List<String> EXPECTED_HEADER = List.of(
            "vaccineId", "cvxCode", "shortDesc", "vaccineStatus",
            "vaccineGroupName", "vaccineGroupCvxCode", "notes");

    private final List<Vaccine> all;
    private final Map<Integer, List<Vaccine>> byCvx;

    public FileVaccineCatalog(
            @Value("${immunization.vaccines.location:classpath:vaccines/vaccines.csv}") Resource resource) {
        List<Vaccine> loaded = load(resource);
        Map<Integer, List<Vaccine>> index = new LinkedHashMap<>();
        for (Vaccine v : loaded) {
            index.computeIfAbsent(v.getCvxCode(), k -> new ArrayList<>()).add(v);
        }
        this.all = Collections.unmodifiableList(loaded);
        this.byCvx = index;
        log.info("Loaded {} vaccine rows ({} CVX codes) from {}",
                all.size(), byCvx.size(), resource.getDescription());
    }

    @Override
    public List<Vaccine> findByCvxCode(Integer cvxCode) {
        if (cvxCode == null) {
            return List.of();
        }
        return Collections.unmodifiableList(byCvx.getOrDefault(cvxCode, List.of()));
    }

    @Override
    public List<Vaccine> findByGroupName(String groupName) {
        if (groupName == null) {
            return List.of();
        }
        String wanted = groupName.trim().toLowerCase(Locale.ROOT);
        List<Vaccine> result = new ArrayList<>();
        for (Vaccine v : all) {
            if (v.getVaccineGroupName().toLowerCase(Locale.ROOT).equals(wanted)) {
                result.add(v);
            }
        }
        return result;
    }

    @Override
    public List<Vaccine> findAll() {
        return all;
    }

    // ------------------------------------------------------------------
    // Loading
    // ------------------------------------------------------------------

    private static List<Vaccine> load(Resource resource) {
        String text;
        try (InputStream in = resource.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read vaccine file " + resource.getDescription(), e);
        }
        if (!text.isEmpty() && text.charAt(0) == '\uFEFF') {
            text = text.substring(1);                       // drop a byte-order mark if an editor added one
        }

        List<List<String>> records = parseCsv(text, resource.getDescription());
        if (records.isEmpty()) {
            throw new IllegalStateException("Vaccine file is empty: " + resource.getDescription());
        }
        if (!records.get(0).equals(EXPECTED_HEADER)) {
            throw new IllegalStateException("Vaccine file " + resource.getDescription()
                    + " has the wrong header. Expected " + EXPECTED_HEADER + " but found " + records.get(0));
        }

        List<Vaccine> vaccines = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            vaccines.add(toVaccine(records.get(i), i + 1, resource.getDescription()));
        }
        if (vaccines.isEmpty()) {
            throw new IllegalStateException("Vaccine file has a header but no rows: " + resource.getDescription());
        }
        return vaccines;
    }

    private static Vaccine toVaccine(List<String> f, int recordNo, String source) {
        if (f.size() != EXPECTED_HEADER.size()) {
            throw bad(source, recordNo, "expected " + EXPECTED_HEADER.size() + " columns but found " + f.size());
        }
        String notes = f.get(6).isBlank() ? null : f.get(6);
        return Vaccine.builder()
                .vaccineId(intField(f.get(0), "vaccineId", recordNo, source))
                .cvxCode(intField(f.get(1), "cvxCode", recordNo, source))
                .shortDesc(textField(f.get(2), "shortDesc", recordNo, source))
                .vaccineStatus(textField(f.get(3), "vaccineStatus", recordNo, source))
                .vaccineGroupName(textField(f.get(4), "vaccineGroupName", recordNo, source))
                .vaccineGroupCVXcode(intField(f.get(5), "vaccineGroupCvxCode", recordNo, source))
                .notes(notes)
                .build();                                   // lastUpdatedDate stays null: the file has no such value
    }

    private static Integer intField(String value, String name, int recordNo, String source) {
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            throw bad(source, recordNo, name + " must be a whole number but was '" + value + "'");
        }
    }

    private static String textField(String value, String name, int recordNo, String source) {
        if (value == null || value.isBlank()) {
            throw bad(source, recordNo, name + " is required");
        }
        return value.trim();
    }

    private static IllegalStateException bad(String source, int recordNo, String message) {
        return new IllegalStateException("Vaccine file " + source + ", record " + recordNo + ": " + message);
    }

    /**
     * Small RFC 4180 reader: commas inside double quotes, "" for a literal quote,
     * quoted fields may contain line breaks, \n or \r\n between records. Blank lines are skipped.
     */
    static List<List<String>> parseCsv(String text, String source) {
        List<List<String>> records = new ArrayList<>();
        List<String> row = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldWasQuoted = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');                  // "" inside quotes is one quote character
                        i++;
                    } else {
                        inQuotes = false;                   // closing quote
                    }
                } else {
                    field.append(c);
                }
            } else if (c == '"' && field.length() == 0 && !fieldWasQuoted) {
                inQuotes = true;                            // opening quote at the start of a field
                fieldWasQuoted = true;
            } else if (c == ',') {
                row.add(field.toString());
                field.setLength(0);
                fieldWasQuoted = false;
            } else if (c == '\n' || c == '\r') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;                                    // treat \r\n as one line break
                }
                row.add(field.toString());
                field.setLength(0);
                fieldWasQuoted = false;
                if (!(row.size() == 1 && row.get(0).isEmpty())) {
                    records.add(row);                       // skip completely blank lines
                }
                row = new ArrayList<>();
            } else {
                field.append(c);
            }
        }
        if (inQuotes) {
            throw new IllegalStateException("Vaccine file " + source + " ends inside a quoted field");
        }
        if (field.length() > 0 || !row.isEmpty()) {         // last line without a trailing newline
            row.add(field.toString());
            records.add(row);
        }
        return records;
    }
}
