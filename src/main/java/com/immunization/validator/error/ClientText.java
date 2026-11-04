package com.immunization.validator.error;

import java.nio.charset.StandardCharsets;

/**
 * Makes client-supplied text safe to return in a JSON response.
 *
 * A JSON body can contain half of a character, such as "\ud83d", and the JSON writer refuses to
 * write one. Returned as is, it would turn a 400 into a 500, or fail a whole batch at the very end.
 */
public final class ClientText {

    private ClientText() { }

    /** Replaces any broken character with "?". */
    public static String wellFormed(String value) {
        return value == null ? null : new String(value.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    /** Keeps the first maxChars whole characters, so an emoji is never cut in half. */
    public static String shorten(String value, int maxChars) {
        if (value == null) {
            return null;
        }
        int characters = Math.min(maxChars, value.codePointCount(0, value.length()));
        return wellFormed(value.substring(0, value.offsetByCodePoints(0, characters)));
    }
}
