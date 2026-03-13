package com.immunization.validator.controller;

import com.immunization.validator.model.*;
import com.immunization.validator.service.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST controller for immunization validation endpoints.
 *
 * V3 change: responses now carry ComplianceStatus (VALID / INVALID / UNDETERMINED).
 * The deprecated {@code valid} boolean field is still populated for backward compatibility.
 * Batch summary logging breaks out all three status buckets.
 *
 * @author Saakad
 * @version 3.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/validate")
@RequiredArgsConstructor
@Validated
public class ValidationController {

    private final ValidationService validationService;

    // ─── Single validation ────────────────────────────────────────────────────

    @PostMapping("/single")
    public ResponseEntity<ValidationResponse> validateSingle(
            @Valid @RequestBody Patient patient,
            @RequestParam("state") String state,
            @RequestParam(value = "age", required = false) Integer age,
            @RequestParam(value = "schoolYear", required = false) String schoolYear,
            @RequestParam(value = "responseMode", defaultValue = "simple") String responseMode,
            HttpServletRequest request) {

        logRequest(request, patient.getId(), responseMode);

        boolean detailedResponse = "detailed".equalsIgnoreCase(responseMode);
        ValidationResponse response = validationService.validate(
                patient, state, age, schoolYear, detailedResponse);

        logResponse(patient.getId(), response.getStatus(), responseMode, null);

        return ResponseEntity.ok(response);
    }

    // ─── Batch validation ─────────────────────────────────────────────────────

    @PostMapping("/batch")
    public ResponseEntity<BatchValidationResponse> validateBatch(
            @Valid @RequestBody BatchValidationRequest batchRequest,
            HttpServletRequest request) {

        String firstId = batchRequest.getPatients() != null && !batchRequest.getPatients().isEmpty()
                ? batchRequest.getPatients().get(0).getId() : "unknown";

        logRequest(request, firstId, batchRequest.getResponseMode());
        log.info("Batch validation: {} patients, state={}, age={}, schoolYear={}, mode={}",
                batchRequest.getPatients() != null ? batchRequest.getPatients().size() : 0,
                batchRequest.getState(), batchRequest.getAge(),
                batchRequest.getSchoolYear(), batchRequest.getResponseMode());

        boolean detailedResponse = "detailed".equalsIgnoreCase(batchRequest.getResponseMode());

        List<ValidationResponse> results = batchRequest.getPatients().stream()
                .map(patient -> validationService.validate(
                        patient,
                        batchRequest.getState(),
                        batchRequest.getAge(),
                        batchRequest.getSchoolYear(),
                        detailedResponse))
                .collect(Collectors.toList());

        BatchValidationResponse response = BatchValidationResponse.builder()
                .results(results)
                .build();

        // V3: log all three buckets
        long validCount        = results.stream().filter(r -> r.getStatus() == ComplianceStatus.VALID).count();
        long invalidCount      = results.stream().filter(r -> r.getStatus() == ComplianceStatus.INVALID).count();
        long undeterminedCount = results.stream().filter(r -> r.getStatus() == ComplianceStatus.UNDETERMINED).count();

        logResponse(firstId,
                validCount == results.size() ? ComplianceStatus.VALID : ComplianceStatus.INVALID,
                batchRequest.getResponseMode(),
                String.format("%d VALID / %d INVALID / %d UNDETERMINED",
                        validCount, invalidCount, undeterminedCount));

        return ResponseEntity.ok(response);
    }

    // ─── Health check ─────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ─── Exception handlers ───────────────────────────────────────────────────

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ValidationResponse> handleMissingParameter(
            MissingServletRequestParameterException ex, HttpServletRequest request) {
        log.warn("Missing required parameter - Source: {}, Parameter: {}, Timestamp: {}",
                getClientIp(request), ex.getParameterName(), System.currentTimeMillis());
        ValidationResponse err = ValidationResponse.builder().build();
        err.setStatusWithBackwardCompatibility(ComplianceStatus.INVALID);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ValidationResponse> handleBadRequest(
            Exception ex, HttpServletRequest request) {
        log.warn("Invalid request - Source: {}, Error: {}, Timestamp: {}",
                getClientIp(request), ex.getMessage(), System.currentTimeMillis());
        ValidationResponse err = ValidationResponse.builder().build();
        err.setStatusWithBackwardCompatibility(ComplianceStatus.INVALID);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(err);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ValidationResponse> handleException(
            Exception ex, HttpServletRequest request) {
        log.error("Error processing request - Source: {}, Error: {}, Timestamp: {}",
                getClientIp(request), ex.getMessage(), System.currentTimeMillis(), ex);
        ValidationResponse err = ValidationResponse.builder().build();
        err.setStatusWithBackwardCompatibility(ComplianceStatus.UNDETERMINED);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(err);
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void logRequest(HttpServletRequest request, String patientId, String responseMode) {
        log.info("Request received - Source: {}, Patient ID: {}, Mode: {}, Timestamp: {}",
                getClientIp(request), maskPatientId(patientId), responseMode, System.currentTimeMillis());
    }

    private void logResponse(String patientId, ComplianceStatus status,
                             String responseMode, String extra) {
        String msg = String.format("Response sent - Patient ID: %s, Status: %s, Mode: %s, Timestamp: %d",
                maskPatientId(patientId), status, responseMode, System.currentTimeMillis());
        if (extra != null && !extra.isEmpty()) msg += ", " + extra;
        log.info(msg);
    }

    private String maskPatientId(String patientId) {
        if (patientId == null || patientId.length() <= 8) return "****";
        return patientId.substring(0, 4) + "****" + patientId.substring(patientId.length() - 4);
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty()) return xri;
        return request.getRemoteAddr();
    }
}