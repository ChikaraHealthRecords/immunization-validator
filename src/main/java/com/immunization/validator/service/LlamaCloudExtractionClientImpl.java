package com.immunization.validator.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;

/**
 * LlamaCloud LlamaExtract v2 client.
 *
 * Flow (v2 REST API, current as of the LlamaCloud docs):
 *   1. Upload the file:      POST /api/v1/beta/files              (multipart) -> file id (dfl-...)
 *   2. Submit extract job:   POST /api/v2/extract?project_id=...  (application/json, file_input=file id)
 *   3. Poll job status:      GET  /api/v2/extract/{jobId}?project_id=...
 *
 * Previously this class posted the file bytes and the schema together as
 * multipart form fields directly to /api/v1/extraction/jobs -- that was the
 * legacy v1 agent-based shape and returned 422 against the live v2 API,
 * which expects a JSON body referencing an already-uploaded file id.
 */
@Slf4j
@Service
public class LlamaCloudExtractionClientImpl implements LlamaCloudExtractionClient {

    private static final String PATIENT_IMMUNIZATIONS_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "patient": {
                  "type": "object",
                  "properties": {
                    "name": { "type": "string" },
                    "dateOfBirth": { "type": "string", "description": "Patient date of birth, formatted as ISO 8601 YYYY-MM-DD" },
                    "medicalRecordNumber": { "type": "string" }
                  }
                },
                "immunizations": {
                  "type": "array",
                  "description": "One entry per administered dose. If a combination vaccine covers multiple vaccine families, emit one entry per family for that date.",
                  "items": {
                    "type": "object",
                    "properties": {
                      "vaccineFamily": { "type": "string", "description": "The vaccine family/group name as printed in the document, e.g. DTAP/DT, Hib, Polio, Pneumopcv, MMR, Varicella" },
                      "administeredDate": { "type": "string", "description": "Date the dose was administered, formatted as ISO 8601 YYYY-MM-DD" }
                    },
                    "required": ["vaccineFamily", "administeredDate"]
                  }
                }
              }
            }
            """;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${llama.cloud.base-url:https://api.cloud.llamaindex.ai}")
    private String baseUrl;

    @Value("${llama.cloud.api-key:}")
    private String apiKey;

    @Value("${llama.cloud.project-id:}")
    private String projectId;

    @Value("${llama.cloud.submit-rate-limit-per-minute:20}")
    private int submitRateLimitPerMinute;

    private final Deque<Instant> recentSubmitTimestamps = new ArrayDeque<>();

    public LlamaCloudExtractionClientImpl(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String submitJob(MultipartFile file) {
        requireConfigured();
        throttle();

        try {
            String fileId = uploadFile(file);
            return createExtractionJob(fileId);
        } catch (IOException e) {
            throw new LlamaCloudClientException("Failed to read uploaded file for submission", e);
        } catch (RestClientException e) {
            throw new LlamaCloudClientException("LlamaCloud job submission failed: " + e.getMessage(), e);
        }
    }

    /** Step 1: upload the raw file, get back a file id (dfl-...). */
    private String uploadFile(MultipartFile file) throws IOException {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });
        body.add("purpose", "extract");

        HttpEntity<MultiValueMap<String, Object>> request = new HttpEntity<>(body, headers);

        JsonNode response = restTemplate.postForObject(
                baseUrl + "/api/v1/beta/files", request, JsonNode.class);

        if (response == null || !response.hasNonNull("id")) {
            throw new LlamaCloudClientException("LlamaCloud file upload response missing id: " + response);
        }
        return response.get("id").asText();
    }

    /** Step 2: submit the extraction job against the uploaded file id. */
    private String createExtractionJob(String fileId) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);

        ObjectNode dataSchema;
        try {
            dataSchema = (ObjectNode) objectMapper.readTree(PATIENT_IMMUNIZATIONS_SCHEMA);
        } catch (IOException e) {
            throw new LlamaCloudClientException("Failed to parse built-in extraction schema", e);
        }

        ObjectNode configuration = objectMapper.createObjectNode();
        configuration.put("tier", "agentic");
        configuration.set("data_schema", dataSchema);
        configuration.put("extraction_target", "per_doc");

        ObjectNode requestBody = objectMapper.createObjectNode();
        requestBody.put("file_input", fileId);
        requestBody.set("configuration", configuration);

        HttpEntity<String> request;
        try {
            request = new HttpEntity<>(objectMapper.writeValueAsString(requestBody), headers);
        } catch (IOException e) {
            throw new LlamaCloudClientException("Failed to serialize extraction job request", e);
        }

        JsonNode response = restTemplate.postForObject(
                extractUrl(null), request, JsonNode.class);

        if (response == null || !response.hasNonNull("id")) {
            throw new LlamaCloudClientException("LlamaCloud extract response missing job id: " + response);
        }
        return response.get("id").asText();
    }

    @Override
    public LlamaCloudJobResult getJobStatus(String llamaExtractJobId) {
        requireConfigured();

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setBearerAuth(apiKey);
            HttpEntity<Void> request = new HttpEntity<>(headers);

            JsonNode response = restTemplate.exchange(
                    extractUrl(llamaExtractJobId),
                    org.springframework.http.HttpMethod.GET,
                    request,
                    JsonNode.class).getBody();

            if (response == null || !response.hasNonNull("status")) {
                throw new LlamaCloudClientException("LlamaCloud status response missing status field: " + response);
            }

            String rawStatus = response.get("status").asText();
            LlamaCloudJobState state = mapStatus(rawStatus);

            LlamaCloudJobResult.LlamaCloudJobResultBuilder result = LlamaCloudJobResult.builder().state(state);

            if (state == LlamaCloudJobState.SUCCESS) {
                JsonNode resultNode = response.get("extract_result") != null
                        ? response.get("extract_result")
                        : response.get("result");
                result.resultJson(resultNode != null ? objectMapper.writeValueAsString(resultNode) : null);
            } else if (state == LlamaCloudJobState.ERROR) {
                JsonNode errorNode = response.get("error");
                result.errorMessage(errorNode != null ? errorNode.asText() : "LlamaCloud reported job failure");
            }

            return result.build();
        } catch (IOException e) {
            throw new LlamaCloudClientException("Failed to parse LlamaCloud status response", e);
        } catch (RestClientException e) {
            throw new LlamaCloudClientException("LlamaCloud status check failed: " + e.getMessage(), e);
        }
    }

    /** Builds /api/v2/extract or /api/v2/extract/{jobId}, always with project_id. */
    private String extractUrl(String jobId) {
        String path = baseUrl + "/api/v2/extract" + (jobId != null ? "/" + jobId : "");
        return path + "?project_id=" + projectId;
    }

    private void requireConfigured() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new LlamaCloudClientException("LLAMA_CLOUD_API_KEY is not configured");
        }
        if (projectId == null || projectId.isBlank()) {
            throw new LlamaCloudClientException("LLAMA_CLOUD_PROJECT_ID is not configured");
        }
    }

    private LlamaCloudJobState mapStatus(String rawStatus) {
        return switch (rawStatus.toUpperCase()) {
            case "SUCCESS", "COMPLETED" -> LlamaCloudJobState.SUCCESS;
            case "ERROR", "FAILED" -> LlamaCloudJobState.ERROR;
            case "PROCESSING", "RUNNING" -> LlamaCloudJobState.PROCESSING;
            default -> LlamaCloudJobState.PENDING;
        };
    }

    /**
     * Simple in-process sliding-window rate limiter so submissions (including
     * any future batch/SIS-sync caller) stay under LlamaCloud's free-tier cap.
     * Blocks briefly rather than rejecting -- acceptable since submitJob already
     * runs off the request thread or the bounded async pool.
     */
    private synchronized void throttle() {
        Instant windowStart = Instant.now().minus(Duration.ofMinutes(1));
        while (!recentSubmitTimestamps.isEmpty() && recentSubmitTimestamps.peekFirst().isBefore(windowStart)) {
            recentSubmitTimestamps.pollFirst();
        }
        if (recentSubmitTimestamps.size() >= submitRateLimitPerMinute) {
            Instant oldest = recentSubmitTimestamps.peekFirst();
            long waitMs = Duration.between(Instant.now(), oldest.plus(Duration.ofMinutes(1))).toMillis();
            if (waitMs > 0) {
                log.warn("LlamaCloud submit rate limit ({}/min) reached, waiting {}ms", submitRateLimitPerMinute, waitMs);
                try {
                    Thread.sleep(waitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new LlamaCloudClientException("Interrupted while waiting for rate limit window", e);
                }
            }
            recentSubmitTimestamps.pollFirst();
        }
        recentSubmitTimestamps.addLast(Instant.now());
    }
}
