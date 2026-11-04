package com.immunization.validator.error;

import lombok.Getter;

/**
 * A request the controller refuses with a 400. The exception message is the
 * customer-facing text, so never put a client-supplied value in it.
 */
@Getter
public class ApiRequestException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    /** Raw client value for the response body. Never log this unescaped. May be null. */
    private final String rejectedValue;

    public ApiRequestException(ErrorCode errorCode, String field, String message) {
        this(errorCode, field, message, null);
    }

    public ApiRequestException(ErrorCode errorCode, String field, String message, String rejectedValue) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
        this.rejectedValue = rejectedValue;
    }
}
