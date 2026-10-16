package com.immunization.validator;

import com.immunization.validator.model.ExtractedImmunizationEntry;
import com.immunization.validator.model.ExtractedPatientInfo;
import com.immunization.validator.model.ExtractedPatientRecord;
import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.Patient;
import com.immunization.validator.service.ExtractedRecordToImmunizationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fixture built from the actual "Xavier Hains" Immunization Summary document
 * (North Shore Physicians Group, DOB 9/18/2015) used to validate the LlamaExtract
 * ingestion pipeline. Dates below are transcribed directly from that document,
 * including the literal duplicate-date rows produced by combo-vaccine columns
 * (e.g. DTaP/Hib/Polio all administered together on the same visit).
 */
class ExtractedRecordToImmunizationMapperTest {

    private ExtractedRecordToImmunizationMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ExtractedRecordToImmunizationMapper();
    }

    @Test
    @DisplayName("Normalizes US slash dates to ISO 8601")
    void testNormalizeDate_UsSlashFormat() {
        assertEquals("2015-09-18", mapper.normalizeDate("9/18/2015"));
        assertEquals("2016-01-14", mapper.normalizeDate("1/14/2016"));
    }

    @Test
    @DisplayName("Leaves already-ISO dates unchanged")
    void testNormalizeDate_AlreadyIso() {
        assertEquals("2015-09-18", mapper.normalizeDate("2015-09-18"));
    }

    @Test
    @DisplayName("Unparseable date returns null rather than throwing")
    void testNormalizeDate_Unparseable() {
        assertNull(mapper.normalizeDate("not-a-date"));
        assertNull(mapper.normalizeDate(null));
    }

    @Test
    @DisplayName("Maps patient info with normalized DOB and MRN as identifier")
    void testMapPatient_XavierHains() {
        ExtractedPatientRecord record = xavierHainsRecord();

        Patient patient = mapper.mapPatient(record, "fallback-id");

        assertEquals("10505940", patient.getId());
        assertEquals("2015-09-18", patient.getBirthDate());
        assertFalse(patient.getImmunizations().isEmpty());
    }

    @Test
    @DisplayName("Falls back to provided id when patient has no MRN or name")
    void testMapPatient_FallsBackToProvidedId() {
        ExtractedPatientRecord record = ExtractedPatientRecord.builder()
                .patient(ExtractedPatientInfo.builder().dateOfBirth("9/18/2015").build())
                .immunizations(List.of())
                .build();

        Patient patient = mapper.mapPatient(record, "job-123");

        assertEquals("job-123", patient.getId());
    }

    @Test
    @DisplayName("Maps immunizations preserving raw vaccine family labels and literal duplicate dates, with no lot number")
    void testMapImmunizations_PreservesDuplicatesAndRawLabels() {
        ExtractedPatientRecord record = xavierHainsRecord();

        List<Immunization> immunizations = mapper.mapImmunizations(record);

        // DTAP/DT: 9 raw rows (3x 11/17/2015, 2x 1/14/2016, 2x 3/15/2016, 1x 10/11/2017, 1x 10/21/2020)
        long dtapCount = immunizations.stream().filter(i -> "DTAP/DT".equals(i.getVaccineCode())).count();
        assertEquals(9, dtapCount);

        long dtapOnFirstVisit = immunizations.stream()
                .filter(i -> "DTAP/DT".equals(i.getVaccineCode()) && "2015-11-17".equals(i.getOccurrenceDateTime()))
                .count();
        assertEquals(3, dtapOnFirstVisit, "Combo-vaccine columns repeat the same family+date");

        assertTrue(immunizations.stream().allMatch(i -> i.getLotNumber() == null),
                "LlamaExtract source records never carry a lot number");

        long hibCount = immunizations.stream().filter(i -> "Hib".equals(i.getVaccineCode())).count();
        assertEquals(7, hibCount);

        long polioCount = immunizations.stream().filter(i -> "Polio".equals(i.getVaccineCode())).count();
        assertEquals(7, polioCount);

        long mmrCount = immunizations.stream().filter(i -> "MMR".equals(i.getVaccineCode())).count();
        assertEquals(2, mmrCount);

        long varicellaCount = immunizations.stream().filter(i -> "Varicella".equals(i.getVaccineCode())).count();
        assertEquals(2, varicellaCount);
    }

    /**
     * Builds the LlamaExtract-shaped record for Xavier Hains, transcribed from
     * Images/Immunization record.jfif. Only the families relevant to MA school
     * requirements are included, plus one COVID-19 row to exercise unmapped
     * pass-through elsewhere.
     */
    static ExtractedPatientRecord xavierHainsRecord() {
        List<ExtractedImmunizationEntry> entries = new ArrayList<>();

        addAll(entries, "DTAP/DT", "11/17/2015", "11/17/2015", "11/17/2015", "1/14/2016", "1/14/2016",
                "3/15/2016", "3/15/2016", "10/11/2017", "10/21/2020");
        addAll(entries, "Hib", "11/17/2015", "11/17/2015", "1/14/2016", "1/14/2016",
                "3/15/2016", "3/15/2016", "10/11/2017");
        addAll(entries, "Polio", "11/17/2015", "11/17/2015", "1/14/2016", "1/14/2016",
                "3/15/2016", "3/15/2016", "10/21/2020");
        addAll(entries, "Pneumopcv", "11/17/2015", "1/14/2016", "3/15/2016", "10/11/2017");
        addAll(entries, "Hepatitis B", "9/19/2015", "10/22/2015", "3/15/2016");
        addAll(entries, "MMR", "9/19/2016", "10/17/2019");
        addAll(entries, "Varicella", "12/20/2016", "10/17/2019");

        return ExtractedPatientRecord.builder()
                .patient(ExtractedPatientInfo.builder()
                        .name("Hains, Xavier")
                        .dateOfBirth("9/18/2015")
                        .medicalRecordNumber("10505940")
                        .build())
                .immunizations(entries)
                .build();
    }

    private static void addAll(List<ExtractedImmunizationEntry> entries, String family, String... dates) {
        for (String date : dates) {
            entries.add(ExtractedImmunizationEntry.builder()
                    .vaccineFamily(family)
                    .administeredDate(date)
                    .build());
        }
    }
}
