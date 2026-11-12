package com.immunization.validator.fhir;

import com.immunization.validator.controller.RequestParameters;
import com.immunization.validator.error.ApiError;
import com.immunization.validator.error.ApiRequestException;
import com.immunization.validator.error.ClientText;
import com.immunization.validator.error.ErrorCode;
import com.immunization.validator.error.ErrorMessages;
import com.immunization.validator.model.ComplianceStatus;
import com.immunization.validator.model.Patient;
import com.immunization.validator.model.ValidationResponse;
import com.immunization.validator.service.ValidationService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * POST /api/v1/validate/fhir: validates FHIR R4 immunization data with the existing engine.
 *
 * The FHIR input is translated into the existing Patient model by {@link FhirPatientMapper} and each
 * patient goes through {@link ValidationService} unchanged, with the same query parameters as /single.
 * See docs/fhir-validation.md.
 *
 * Logging: counts, codes and masked ids only. The body is never logged.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/validate")
@RequiredArgsConstructor
public class FhirValidationController {

    static final String FHIR_JSON = "application/fhir+json";
    static final String NDJSON = "application/x-ndjson";
    static final String FHIR_NDJSON = "application/fhir+ndjson";

    private final ValidationService validationService;
    private final RequestParameters requestParameters;
    private final FhirPatientMapper mapper = new FhirPatientMapper();

    /**
     * The body is taken as bytes and read as UTF-8, the only encoding FHIR JSON allows, whatever
     * the content type: Spring would otherwise read an NDJSON body as ISO-8859-1.
     */
    @PostMapping(value = "/fhir",
            consumes = {MediaType.APPLICATION_JSON_VALUE, FHIR_JSON, NDJSON, FHIR_NDJSON})
    public ResponseEntity<FhirValidationResponse> validateFhir(
            @RequestBody byte[] body,
            @RequestParam("state") String state,
            @RequestParam(value = "age", required = false) Integer age,
            @RequestParam(value = "schoolYear", required = false) String schoolYear,
            @RequestParam(value = "responseMode", defaultValue = "simple") String responseMode,
            HttpServletRequest request) {

        state = requestParameters.requireState(state);

        log.info("FHIR request received - Source: {}, Mode: {}, Bytes: {}, Timestamp: {}",
                RequestParameters.clientIp(request), RequestParameters.safeForLog(responseMode),
                body.length, System.currentTimeMillis());

        FhirMappingResult mapped = map(new String(body, StandardCharsets.UTF_8));
        logMapping(mapped);

        boolean detailedResponse = "detailed".equalsIgnoreCase(responseMode);

        // One patient that fails must not cost the others their results, as on /batch.
        List<ValidationResponse> results = new ArrayList<>();
        List<FhirMappingResult.PatientMapping> mapping = new ArrayList<>();
        for (FhirMappingResult.MappedPatient patient : mapped.getPatients()) {
            results.add(validatePatient(patient.getChikara(), state, age, schoolYear, detailedResponse));
            mapping.add(patient.getMapping());
        }

        int failedCount = (int) results.stream().filter(r -> r.getError() != null).count();

        FhirValidationResponse response = FhirValidationResponse.builder()
                .results(results)
                .mapping(mapping)
                .unattributed(mapped.getUnattributed())
                .ignoredResourceTypes(mapped.getIgnored())
                .message(failedCount > 0 ? ErrorMessages.SOME_RECORDS_FAILED : null)
                .failedCount(failedCount > 0 ? failedCount : null)
                .build();

        log.info("FHIR response sent - {} patients: {} VALID / {} INVALID / {} UNDETERMINED / {} FAILED",
                results.size(), count(results, ComplianceStatus.VALID), count(results, ComplianceStatus.INVALID),
                count(results, ComplianceStatus.UNDETERMINED), failedCount);

        return ResponseEntity.ok(response);
    }

    /** Input that can't be turned into patients fails the whole request, with an existing message. */
    private FhirMappingResult map(String text) {
        try {
            return mapper.map(text);
        } catch (FhirMappingException ex) {
            // Field and reason are server-defined (such as entry[3].resource.patient), never client text.
            log.warn("FHIR input rejected - errorCode: {}, field: {}, reason: {}",
                    ex.getErrorCode(), ex.getField(), ex.getReason());
            throw new ApiRequestException(ex.getErrorCode(), ex.getField(), messageFor(ex));
        }
    }

    private static String messageFor(FhirMappingException ex) {
        if (ex.getErrorCode() == ErrorCode.MALFORMED_REQUEST) {
            return ErrorMessages.MALFORMED_REQUEST;
        }
        if ("id".equals(ex.getField())) {
            return ErrorMessages.PATIENT_ID_REQUIRED;
        }
        return ErrorMessages.INVALID_REQUEST;
    }

    /** Validates one mapped patient. Never throws: anything that goes wrong becomes an error item. */
    private ValidationResponse validatePatient(Patient patient, String state, Integer age, String schoolYear,
                                               boolean detailedResponse) {
        String id = ClientText.wellFormed(patient.getId());
        try {
            if (id == null || id.isBlank()) {
                log.warn("FHIR patient rejected - Patient ID: {}, errorCode: {}, field: id",
                        RequestParameters.maskPatientId(id), ErrorCode.VALIDATION_FAILED);
                return failedItem(id, new ApiError.FieldProblem(
                        "id", ErrorCode.VALIDATION_FAILED, ErrorMessages.PATIENT_ID_REQUIRED));
            }
            ValidationResponse response = validationService.validate(patient, state, age, schoolYear, detailedResponse);
            response.setPatientId(id);
            return response;
        } catch (RuntimeException ex) {
            log.error("FHIR patient could not be processed - Patient ID: {}", RequestParameters.maskPatientId(id), ex);
            return failedItem(id, new ApiError.FieldProblem(
                    null, ErrorCode.RECORD_PROCESSING_FAILED, ErrorMessages.RECORD_FAILED));
        }
    }

    private static ValidationResponse failedItem(String id, ApiError.FieldProblem problem) {
        return ValidationResponse.builder().patientId(id).error(problem).build();
    }

    private static void logMapping(FhirMappingResult mapped) {
        int received = 0;
        int doses = 0;
        int excluded = 0;
        int warnings = 0;
        for (FhirMappingResult.MappedPatient patient : mapped.getPatients()) {
            received += patient.getMapping().getReceived();
            doses += patient.getMapping().getMapped();
            excluded += patient.getMapping().getExcluded().size();
            warnings += patient.getMapping().getWarnings().size();
        }
        int ignored = mapped.getIgnored().values().stream().mapToInt(Integer::intValue).sum();
        log.info("FHIR input mapped - patients: {}, immunizations received: {}, mapped: {}, excluded: {}, "
                        + "mapping warnings: {}, unattributed: {}, ignored resources: {}",
                mapped.getPatients().size(), received, doses, excluded, warnings,
                mapped.getUnattributed().size(), ignored);
    }

    private static long count(List<ValidationResponse> results, ComplianceStatus status) {
        return results.stream().filter(r -> r.getStatus() == status).count();
    }
}
