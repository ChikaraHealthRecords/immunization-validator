package com.immunization.validator.service;

/**
 * Raised when a call to the LlamaCloud extraction API fails (network error,
 * non-2xx response, or malformed response body).
 */
public class LlamaCloudClientException extends RuntimeException {
    public LlamaCloudClientException(String message) {
        super(message);
    }

    public LlamaCloudClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
