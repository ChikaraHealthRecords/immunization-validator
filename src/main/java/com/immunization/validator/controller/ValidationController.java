package com.immunization.validator.controller;

import com.immunization.validator.error.ApiError;
import com.immunization.validator.error.ApiRequestException;
import com.immunization.validator.error.ClientText;
import com.immunization.validator.error.ErrorCode;
import com.immunization.validator.error.ErrorMessages;
import com.immunization.validator.model.*;
import com.immunization.validator.service.RequirementsService;
import com.immunization.validator.service.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

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
    private final RequirementsService requirementsService;
    private final Validator validator;

    // ─── Single validation ────────────────────────────────────────────────────

    @PostMapping("/single")
    public ResponseEntity<ValidationResponse> validateSingle(
            @Valid @RequestBody Patient patient,
            @RequestParam("state") String state,
            @RequestParam(value = "age", required = false) Integer age,
            @RequestParam(value = "schoolYear", required = false) String schoolYear,
            @RequestParam(value = "responseMode", defaultValue = "simple") String responseMode,
            HttpServletRequest request) {

        // state=  (present but empty) is the same mistake as leaving it out.
        if (state.isBlank()) {
            throw new ApiRequestException(ErrorCode.MISSING_PARAMETER, "state", ErrorMessages.SELECT_STATE);
        }
        requireSupportedState(state);

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

        requireSupportedState(batchRequest.getState());

        String firstId =batchRequest.getPatients() != null && !batchRequest.getPatients().isEmpty()
                && batchRequest.getPatients().get(0) != null
                ? batchRequest.getPatients().get(0).getId() : "unknown";

        logRequest(request, firstId, batchRequest.getResponseMode());
        log.info("Batch validation: {} patients, state={}, age={}, schoolYear={}, mode={}",
                batchRequest.getPatients() != null ? batchRequest.getPatients().size() : 0,
                batchRequest.getState(), batchRequest.getAge(),
                batchRequest.getSchoolYear(), batchRequest.getResponseMode());

        boolean detailedResponse = "detailed".equalsIgnoreCase(batchRequest.getResponseMode());

        // One bad record must not cost the others their results, so each gets its own check.
        List<ValidationResponse> results = new ArrayList<>();
        for (Patient patient : batchRequest.getPatients()) {
            results.add(validateBatchPatient(patient, batchRequest, detailedResponse));
        }

        long validCount        = results.stream().filter(r -> r.getStatus() == ComplianceStatus.VALID).count();
        long invalidCount      = results.stream().filter(r -> r.getStatus() == ComplianceStatus.INVALID).count();
        long undeterminedCount = results.stream().filter(r -> r.getStatus() == ComplianceStatus.UNDETERMINED).count();
        int failedCount        = (int) results.stream().filter(r -> r.getError() != null).count();

        BatchValidationResponse response = BatchValidationResponse.builder()
                .results(results)
                .message(failedCount > 0 ? ErrorMessages.SOME_RECORDS_FAILED : null)
                .failedCount(failedCount > 0 ? failedCount : null)
                .build();

        log.info("Batch response sent - {} patients: {} VALID / {} INVALID / {} UNDETERMINED / {} FAILED",
                results.size(), validCount, invalidCount, undeterminedCount, failedCount);

        return ResponseEntity.ok(response);
    }

    /** Validates one batch patient. Never throws: anything that goes wrong becomes an error item. */
    private ValidationResponse validateBatchPatient(Patient patient, BatchValidationRequest batchRequest,
                                                    boolean detailedResponse) {
        // Broken characters in the id would stop the whole response being written.
        String id = patient != null ? ClientText.wellFormed(patient.getId()) : null;
        try {
            ApiError.FieldProblem problem = firstProblem(patient);
            if (problem != null) {
                log.warn("Batch record rejected - Patient ID: {}, errorCode: {}, field: {}",
                        maskPatientId(id), problem.getErrorCode(), problem.getField());
                return failedItem(id, problem);
            }
            ValidationResponse response = validationService.validate(
                    patient,
                    batchRequest.getState(),
                    batchRequest.getAge(),
                    batchRequest.getSchoolYear(),
                    detailedResponse);
            response.setPatientId(id);
            return response;
        } catch (RuntimeException ex) {
            log.error("Batch record could not be processed - Patient ID: {}", maskPatientId(id), ex);
            return failedItem(id, new ApiError.FieldProblem(
                    null, ErrorCode.RECORD_PROCESSING_FAILED, ErrorMessages.RECORD_FAILED));
        }
    }

    /** The first failing field of a batch patient, or null if the patient is fine. */
    private ApiError.FieldProblem firstProblem(Patient patient) {
        if (patient == null) {
            return new ApiError.FieldProblem("id", ErrorCode.VALIDATION_FAILED, ErrorMessages.PATIENT_ID_REQUIRED);
        }
        return validator.validate(patient).stream()
                .min(Comparator.comparing((ConstraintViolation<Patient> v) -> v.getPropertyPath().toString()))
                .map(v -> new ApiError.FieldProblem(
                        ErrorMessages.jsonField(v.getPropertyPath().toString()),
                        ErrorCode.VALIDATION_FAILED,
                        v.getMessage()))
                .orElse(null);
    }

    private ValidationResponse failedItem(String id, ApiError.FieldProblem problem) {
        return ValidationResponse.builder().patientId(id).error(problem).build();
    }

    // ─── Health check ─────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("OK");
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    /**
     * Rejects a state with no rule file. The match is exact, so "ma" is not "MA".
     * Runs before anything else logs the state, because the value comes straight from the client.
     */
    private void requireSupportedState(String state) {
        if (!requirementsService.hasRequirements(state)) {
            log.warn("Unsupported state rejected: {}", safeForLog(state));
            throw new ApiRequestException(ErrorCode.UNSUPPORTED_STATE, "state", ErrorMessages.UNSUPPORTED_STATE, state);
        }
    }

    /** Keeps a client-supplied value from forging log lines: no line breaks or odd characters, short. */
    private String safeForLog(String value) {
        String cleaned = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32) + "...";
    }

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