package com.immunization.validator.fhir;

import com.immunization.validator.error.ErrorCode;
import lombok.Getter;

/**
 * FHIR input that can't be turned into patients at all, so the whole request fails.
 *
 * The reason is a fixed, server-defined code such as NOT_JSON, never text taken from the input,
 * so it is safe to log. It is not customer-facing: the controller answers with an existing message.
 */
@Getter
public class FhirMappingException extends RuntimeException {

    private final ErrorCode errorCode;

    /** The failing field, such as "entry[1].resource.patient", or null when the error isn't about one. */
    private final String field;

    /** Machine-readable reason code, for example NOT_JSON or NO_PATIENT_REFERENCE. */
    private final String reason;

    public FhirMappingException(ErrorCode errorCode, String field, String reason) {
        super(reason);
        this.errorCode = errorCode;
        this.field = field;
        this.reason = reason;
    }
}
