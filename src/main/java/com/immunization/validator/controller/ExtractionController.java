package com.immunization.validator.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.immunization.validator.model.ExtractionJob;
import com.immunization.validator.model.ExtractionJobResponse;
import com.immunization.validator.model.ExtractionJobStatus;
import com.immunization.validator.repository.ExtractionJobRepository;
import com.immunization.validator.service.DocumentExtractionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * REST API for async document extraction ingestion.
 *
 * POST /api/extraction submits a document for background processing and
 * returns immediately with {jobId, status}. GET /api/extraction/{jobId}
 * returns the current status, including the tri-state validation verdicts
 * once COMPLETED, or the error once FAILED.
 */
@Slf4j
@RestController
@RequestMapping("/api/extraction")
@RequiredArgsConstructor
public class ExtractionController {

    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration WAIT_POLL_INTERVAL = Duration.ofMillis(500);

    private final DocumentExtractionService documentExtractionService;
    private final ExtractionJobRepository extractionJobRepository;
    private final ObjectMapper objectMapper;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ExtractionJobResponse> submit(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "wait", defaultValue = "false") boolean wait) {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ExtractionJob job = documentExtractionService.submitJob(file);

        if (job.getStatus() != ExtractionJobStatus.FAILED) {
            documentExtractionService.pollAndProcess(job.getId());
        }

        if (wait) {
            job = waitForTerminalStatus(job.getId());
        }

        return ResponseEntity.ok(toResponse(job));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ExtractionJobResponse> getStatus(@PathVariable UUID jobId) {
        return extractionJobRepository.findById(jobId)
                .map(job -> ResponseEntity.ok(toResponse(job)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).build());
    }

    /**
     * Long-polls server-side (up to ~15s) so a single-upload UI can feel
     * synchronous without a separate code path.
     */
    private ExtractionJob waitForTerminalStatus(UUID jobId) {
        Instant deadline = Instant.now().plus(WAIT_TIMEOUT);
        ExtractionJob latest = extractionJobRepository.findById(jobId).orElse(null);

        while (latest != null && !latest.getStatus().isTerminal() && Instant.now().isBefore(deadline)) {
            try {
                Thread.sleep(WAIT_POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            latest = extractionJobRepository.findById(jobId).orElse(latest);
        }

        return latest;
    }

    private ExtractionJobResponse toResponse(ExtractionJob job) {
        ExtractionJobResponse.ExtractionJobResponseBuilder response = ExtractionJobResponse.builder()
                .jobId(job.getId().toString())
                .status(job.getStatus());

        if (job.getStatus() == ExtractionJobStatus.COMPLETED && job.getValidationResult() != null) {
            response.validationResult(readAsJson(job.getValidationResult()));
        } else if (job.getStatus() == ExtractionJobStatus.FAILED) {
            response.errorMessage(job.getErrorMessageUser());
        }

        return response.build();
    }

    private JsonNode readAsJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            log.error("Failed to parse stored validationResult as JSON", e);
            return null;
        }
    }
}
