package com.immunization.validator.service;

/**
 * LlamaCloud's own job status vocabulary, as returned by the extraction
 * jobs API. Kept distinct from {@link com.immunization.validator.model.ExtractionJobStatus},
 * which is our internal lifecycle state.
 */
public enum LlamaCloudJobState {
    PENDING,
    PROCESSING,
    SUCCESS,
    ERROR
}
