package com.immunization.validator.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * The body of every API error response. See docs/api-errors.md.
 *
 * The HTTP status is called "httpStatus" because "status" already means the compliance
 * result on validation responses. An error response never carries a compliance status.
 */
@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiError {

    private final int httpStatus;
    private final ErrorCode errorCode;

    /** The first failing field or parameter, when the error is about one. */
    private final String field;

    /** The type the field needed, such as "integer", when a value had the wrong type. */
    private final String expectedType;

    /** The value that was refused, shortened. It came from the client: escape it before displaying it. */
    private final String rejectedValue;

    private final String message;
    private final String requestId;

    /** Every failing field when more than one failed, or the explanation of an unreadable record. */
    private final List<FieldProblem> details;

    /** One failing field, or an explanation with no field. */
    @Getter
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FieldProblem {
        private final String field;
        private final ErrorCode errorCode;
        private final String message;
    }
}
