package com.immunization.validator.service;

import com.immunization.validator.model.ExtractedImmunizationEntry;
import com.immunization.validator.model.ExtractedPatientRecord;
import com.immunization.validator.model.Immunization;
import com.immunization.validator.model.Patient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps a LlamaExtract JSON result (deserialized as {@link ExtractedPatientRecord})
 * into the existing domain model. No lot number is set for this source type --
 * this is a patient-reported/EHR-exported document, not a registry feed with
 * verified administration records.
 *
 * Raw vaccine family labels are carried through as-is; canonicalizing them to
 * the vaccine group names RequirementsService understands is
 * {@link VaccineFamilyMappingService}'s job, applied by the caller.
 */
@Slf4j
@Component
public class ExtractedRecordToImmunizationMapper {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter US_SLASH = DateTimeFormatter.ofPattern("M/d/yyyy");

    public List<Immunization> mapImmunizations(ExtractedPatientRecord record) {
        List<Immunization> immunizations = new ArrayList<>();
        if (record == null || record.getImmunizations() == null) {
            return immunizations;
        }

        for (ExtractedImmunizationEntry entry : record.getImmunizations()) {
            String normalizedDate = normalizeDate(entry.getAdministeredDate());
            if (normalizedDate == null) {
                log.warn("Skipping immunization entry with unparseable date: family='{}', date='{}'",
                        entry.getVaccineFamily(), entry.getAdministeredDate());
                continue;
            }

            immunizations.add(Immunization.builder()
                    .vaccineCode(entry.getVaccineFamily())
                    .occurrenceDateTime(normalizedDate)
                    .lotNumber(null)
                    .build());
        }

        return immunizations;
    }

    public Patient mapPatient(ExtractedPatientRecord record, String fallbackId) {
        String birthDate = record != null && record.getPatient() != null
                ? normalizeDate(record.getPatient().getDateOfBirth())
                : null;

        String patientId = resolvePatientId(record, fallbackId);

        return Patient.builder()
                .id(patientId)
                .birthDate(birthDate)
                .immunizations(mapImmunizations(record))
                .build();
    }

    private String resolvePatientId(ExtractedPatientRecord record, String fallbackId) {
        if (record != null && record.getPatient() != null) {
            String mrn = record.getPatient().getMedicalRecordNumber();
            if (mrn != null && !mrn.isBlank()) {
                return mrn.trim();
            }
            String name = record.getPatient().getName();
            if (name != null && !name.isBlank()) {
                return name.trim();
            }
        }
        return fallbackId;
    }

    /**
     * Normalizes a date string to ISO 8601 (YYYY-MM-DD). LlamaExtract is asked
     * to return ISO dates via the schema description, but the source documents
     * render dates as e.g. "9/18/2015" -- this is a defensive fallback in case
     * the extraction doesn't fully comply.
     */
    public String normalizeDate(String rawDate) {
        if (rawDate == null || rawDate.isBlank()) {
            return null;
        }

        String trimmed = rawDate.trim();
        try {
            return LocalDate.parse(trimmed, ISO).format(ISO);
        } catch (DateTimeParseException ignored) {
            // fall through to alternate format
        }

        try {
            return LocalDate.parse(trimmed, US_SLASH).format(ISO);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
