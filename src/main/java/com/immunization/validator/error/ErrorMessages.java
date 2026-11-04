package com.immunization.validator.error;

import java.util.Map;

/**
 * Customer-facing error text. Field messages on request bodies live in the
 * Bean Validation annotations on the model classes.
 */
public final class ErrorMessages {

    public static final String SELECT_STATE = "Please select a state.";
    public static final String UNSUPPORTED_STATE = "Validation is not currently supported for the selected state.";
    public static final String AGE_WHOLE_NUMBER = "Age must be a whole number.";
    public static final String MALFORMED_REQUEST = "We couldn't read the submitted record. Please check its format.";
    public static final String UNSUPPORTED_MEDIA_TYPE = "Please submit your record in JSON format.";
    public static final String INTERNAL_ERROR =
            "Something went wrong on our side. Please try again, and quote this request ID if it continues.";
    public static final String PATIENT_ID_REQUIRED = "Patient identifier is required.";
    public static final String CORRECT_DATE = "Please provide or correct the vaccination date.";
    public static final String UNKNOWN_VACCINE =
            "We couldn't identify one of the vaccines. Please check the vaccine information.";
    public static final String SOME_RECORDS_FAILED =
            "Some records could not be processed. Please review the affected records.";

    // Not in the approved table: listed in the Phase 1 report for approval.
    public static final String METHOD_NOT_ALLOWED = "This action isn't available at this address.";
    public static final String NOT_FOUND = "We couldn't find what you were looking for. Please check the address.";
    public static final String REQUEST_NOT_SUPPORTED = "We couldn't process this request. Please check it and try again.";
    public static final String INVALID_REQUEST = "Please check the submitted information.";
    public static final String NOT_VALID_JSON = "The record is not valid JSON";
    public static final String NOT_EXPECTED_FORMAT = "The record is not in the expected format";
    public static final String NO_RECORD = "No record was submitted";
    public static final String RECORD_FAILED = "This record could not be processed. Please review it.";
    public static final String EXCEPTION_IGNORED = "We couldn't read one of the vaccine exceptions, so it was ignored.";

    private static final Map<String, String> MISSING_PARAMETER = Map.of(
            "state", SELECT_STATE,
            "q", "Please enter a search term.");

    private static final Map<String, String> WRONG_TYPE = Map.of(
            "age", AGE_WHOLE_NUMBER,
            "cvxCode", "CVX code must be a whole number.");

    private ErrorMessages() { }

    public static String missingParameter(String name) {
        return MISSING_PARAMETER.getOrDefault(name, INVALID_REQUEST);
    }

    /** Reports a field under its JSON name: the Java property is "immunizations", the JSON key is "immunization". */
    public static String jsonField(String path) {
        return path.startsWith("immunizations") ? "immunization" + path.substring("immunizations".length()) : path;
    }

    public static String wrongType(String name) {
        // Map.of throws on a null key, and a type mismatch outside a request parameter has no name.
        return name == null ? INVALID_REQUEST : WRONG_TYPE.getOrDefault(name, INVALID_REQUEST);
    }
}
