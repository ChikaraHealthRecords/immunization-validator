package com.example.docextract.exception;

public class ExtractionFailedException extends RuntimeException {
    public ExtractionFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
