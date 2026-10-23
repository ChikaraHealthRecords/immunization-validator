package com.immunization.validator.service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/**
 * One place that turns the text in occurrenceDateTime into a LocalDate.
 *
 * Why it exists:
 *  - ValidationService used the raw text for sorting and de-duplication, so
 *    "2024-01-10" and "2024-01-10T09:00:00" looked like two different days.
 *  - The evaluators each carry their own private copy of this parser.
 *    This class is the shared version. The evaluators are left alone for now.
 *
 * Plain Java on purpose: no Spring, no Lombok, so it can be tested without a context.
 */
public final class ImmunizationDates {

    // Utility class: nobody should create an instance.
    private ImmunizationDates() { }

    /**
     * @param text value of occurrenceDateTime, may be null or blank
     * @return the calendar day, or null when the text is empty or not a date we understand
     */
    public static LocalDate parse(String text) {
        // Nothing to parse: null, empty, or only spaces.
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.trim();                       // ignore stray spaces around the value

        try {
            return LocalDate.parse(value);                // "2024-01-10"
        } catch (DateTimeParseException ignored) {
            // not a plain date, try the next shape
        }
        try {
            return LocalDateTime.parse(value).toLocalDate();      // "2024-01-10T09:00:00"
        } catch (DateTimeParseException ignored) {
            // not a local date-time, try the next shape
        }
        try {
            return OffsetDateTime.parse(value).toLocalDate();     // "2024-01-10T09:00:00Z" or "+05:00"
        } catch (DateTimeParseException ignored) {
            // not an offset date-time either
        }
        return null;                                      // "03/01/2019", "yesterday", etc.
    }
}
