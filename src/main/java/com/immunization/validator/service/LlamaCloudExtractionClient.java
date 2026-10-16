package com.immunization.validator.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * Client for LlamaCloud's LlamaExtract API. Kept behind an interface so the
 * request/response shape (currently targeting the /api/v1/extraction/jobs
 * endpoints as specified) can be swapped out -- e.g. for the newer /api/v2/extract
 * shape, or for webhook-based delivery -- without touching callers.
 */
public interface LlamaCloudExtractionClient {

    /**
     * Submits a document for extraction using the patient+immunizations schema.
     *
     * @param file the uploaded immunization summary document
     * @return the LlamaCloud job id, used for subsequent status polling
     * @throws LlamaCloudClientException on submission failure
     */
    String submitJob(MultipartFile file);

    /**
     * Polls the current status of a previously submitted job.
     *
     * @param llamaExtractJobId job id returned by {@link #submitJob}
     * @return the current job state, with result/error populated when terminal
     * @throws LlamaCloudClientException on a request failure (distinct from the
     *         job itself reaching an ERROR state, which is returned normally)
     */
    LlamaCloudJobResult getJobStatus(String llamaExtractJobId);
}
