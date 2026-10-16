package com.immunization.validator.model;

/**
 * Lifecycle status of an async document extraction job.
 *
 * PENDING    - row created, not yet submitted to LlamaCloud
 * SUBMITTED  - submitted to LlamaCloud, awaiting completion
 * PROCESSING - LlamaCloud has acknowledged the job is running
 * COMPLETED  - result received, mapped, and validated
 * FAILED     - submission error, LlamaCloud error, or mapping/validation error
 */
public enum ExtractionJobStatus {
    PENDING,
    SUBMITTED,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
