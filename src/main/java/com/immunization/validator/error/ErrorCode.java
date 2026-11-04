package com.immunization.validator.error;

/**
 * Stable machine-readable codes returned in the "errorCode" field of an error response.
 * Clients may branch on these, so treat renaming one as a breaking API change.
 */
public enum ErrorCode {
    VALIDATION_FAILED,
    MISSING_PARAMETER,
    INVALID_PARAMETER_TYPE,
    MALFORMED_REQUEST,
    UNSUPPORTED_MEDIA_TYPE,
    UNSUPPORTED_STATE,
    METHOD_NOT_ALLOWED,
    NOT_FOUND,
    /** Any other 4xx from Spring MVC, for example 406 when the Accept header rules out JSON. */
    REQUEST_NOT_SUPPORTED,
    RECORD_PROCESSING_FAILED,
    INTERNAL_ERROR
}
