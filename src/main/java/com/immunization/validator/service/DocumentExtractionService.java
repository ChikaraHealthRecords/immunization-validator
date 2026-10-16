package com.immunization.validator.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.immunization.validator.model.*;
import com.immunization.validator.repository.ExtractionJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Orchestrates async ingestion of immunization summary documents via LlamaCloud's
 * LlamaExtract API:
 *
 * submitJob: persists a PENDING row, submits to LlamaCloud, moves to SUBMITTED.
 * pollAndProcess: polls a single job's LlamaCloud status; on completion, maps the
 *   result through ExtractedRecordToImmunizationMapper -> VaccineFamilyMappingService
 *   -> ValidationService.deduplicateImmunizations() -> requirement evaluation for
 *   every school year the configured state defines, storing the tri-state verdicts.
 * sweepNonTerminalJobs: a @Scheduled safety net that re-polls any job not yet in a
 *   terminal state, so processing survives app restarts and isn't solely dependent
 *   on the post-submit kick.
 *
 * Pipeline stage logging: every job emits four PIPELINE_STAGE log lines as it moves
 * through UPLOAD_RECEIVED -> LLAMA_SUBMITTED -> LLAMA_COMPLETED -> VALIDATION_COMPLETED,
 * each carrying jobId and elapsedMs (time since upload). This gives a single grep-able
 * timeline per job instead of having to correlate different free-text log messages:
 *
 *   grep "PIPELINE_STAGE" app.log | grep <jobId>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentExtractionService {

    private final ExtractionJobRepository extractionJobRepository;
    private final LlamaCloudExtractionClient llamaCloudExtractionClient;
    private final ExtractedRecordToImmunizationMapper mapper;
    private final VaccineFamilyMappingService vaccineFamilyMappingService;
    private final ValidationService validationService;
    private final RequirementsService requirementsService;
    private final ObjectMapper objectMapper;

    @Value("${immunization.extraction.default-state:MA}")
    private String defaultState;

    public ExtractionJob submitJob(MultipartFile file) {
        ExtractionJob job = ExtractionJob.builder()
                .id(UUID.randomUUID())
                .status(ExtractionJobStatus.PENDING)
                .sourceFileName(file.getOriginalFilename())
                .uploadedAt(ZonedDateTime.now())
                .build();
        job = extractionJobRepository.save(job);

        logStage("UPLOAD_RECEIVED", job);

        try {
            String llamaExtractJobId = llamaCloudExtractionClient.submitJob(file);
            job.setLlamaExtractJobId(llamaExtractJobId);
            job.setStatus(ExtractionJobStatus.SUBMITTED);
            logStage("LLAMA_SUBMITTED", job);
        } catch (Exception e) {
            log.error("Failed to submit extraction job {} to LlamaCloud", job.getId(), e);
            job.setStatus(ExtractionJobStatus.FAILED);
            job.setErrorMessageInternal(e.getMessage());
            job.setErrorMessageUser("Failed to submit document for processing. Please try again later.");
        }

        return extractionJobRepository.save(job);
    }

    @Async("extractionTaskExecutor")
    public void pollAndProcess(UUID jobId) {
        Optional<ExtractionJob> maybeJob = extractionJobRepository.findById(jobId);
        if (maybeJob.isEmpty()) {
            log.warn("pollAndProcess called for unknown job {}", jobId);
            return;
        }

        ExtractionJob job = maybeJob.get();
        if (job.getStatus().isTerminal()) {
            return; // already completed/failed, e.g. by a concurrent sweep/kick
        }
        if (job.getLlamaExtractJobId() == null) {
            return; // never successfully submitted, nothing to poll yet
        }

        try {
            LlamaCloudJobResult result = llamaCloudExtractionClient.getJobStatus(job.getLlamaExtractJobId());
            switch (result.getState()) {
                case PENDING, PROCESSING -> markProcessing(job);
                case SUCCESS -> {
                    logStage("LLAMA_COMPLETED", job);
                    completeJob(job, result.getResultJson());
                }
                case ERROR -> failJob(job,
                        result.getErrorMessage() != null ? result.getErrorMessage() : "LlamaCloud reported job failure",
                        "Document extraction failed. Please verify the document is readable and try again.");
            }
        } catch (Exception e) {
            log.error("Error polling/processing extraction job {}", jobId, e);
            failJob(job, e.getMessage(), "An error occurred while processing your document. Please try again later.");
        }
    }

    @Scheduled(fixedDelayString = "${llama.cloud.poll-interval-ms:15000}")
    public void sweepNonTerminalJobs() {
        List<ExtractionJob> nonTerminal = extractionJobRepository.findByStatusIn(
                List.of(ExtractionJobStatus.SUBMITTED, ExtractionJobStatus.PROCESSING));

        if (!nonTerminal.isEmpty()) {
            log.debug("Sweep found {} non-terminal extraction job(s)", nonTerminal.size());
        }

        for (ExtractionJob job : nonTerminal) {
            pollAndProcess(job.getId());
        }
    }

    private void markProcessing(ExtractionJob job) {
        if (job.getStatus() != ExtractionJobStatus.PROCESSING) {
            job.setStatus(ExtractionJobStatus.PROCESSING);
            extractionJobRepository.save(job);
        }
    }

    private void completeJob(ExtractionJob job, String resultJson) {
        try {
            job.setResultJson(resultJson);

            ExtractedPatientRecord record = objectMapper.readValue(resultJson, ExtractedPatientRecord.class);
            Patient patient = mapper.mapPatient(record, job.getId().toString());

            List<Immunization> canonicalImmunizations = patient.getImmunizations().stream()
                    .map(imm -> Immunization.builder()
                            .vaccineCode(vaccineFamilyMappingService.mapToCanonicalGroup(imm.getVaccineCode()))
                            .occurrenceDateTime(imm.getOccurrenceDateTime())
                            .lotNumber(imm.getLotNumber())
                            .build())
                    .collect(Collectors.toList());

            patient.setImmunizations(validationService.deduplicateImmunizations(canonicalImmunizations));
            job.setPatientIdentifier(patient.getId());

            Map<String, ValidationResponse> resultsBySchoolYear = new LinkedHashMap<>();
            for (String schoolYear : availableSchoolYears(defaultState)) {
                resultsBySchoolYear.put(schoolYear,
                        validationService.validate(patient, defaultState, null, schoolYear, true));
            }

            job.setValidationResult(objectMapper.writeValueAsString(resultsBySchoolYear));
            job.setStatus(ExtractionJobStatus.COMPLETED);
            extractionJobRepository.save(job);

            logStage("VALIDATION_COMPLETED", job);
            log.info("Extraction job {} completed with verdicts for {} school year(s)",
                    job.getId(), resultsBySchoolYear.size());
        } catch (Exception e) {
            log.error("Failed to map/validate extraction job {} result", job.getId(), e);
            failJob(job, "Failed to process extraction result: " + e.getMessage(),
                    "We were unable to process the extracted data from your document. Please try again later.");
        }
    }

    private void failJob(ExtractionJob job, String internalMessage, String userMessage) {
        job.setStatus(ExtractionJobStatus.FAILED);
        job.setErrorMessageInternal(internalMessage);
        job.setErrorMessageUser(userMessage);
        extractionJobRepository.save(job);
        log.warn("Extraction job {} marked FAILED: {}", job.getId(), internalMessage);
    }

    private List<String> availableSchoolYears(String stateCode) {
        StateRequirements stateRequirements = requirementsService.getRequirementsByState().get(stateCode);
        if (stateRequirements == null || stateRequirements.getRequirementsBySchoolYear() == null) {
            return new ArrayList<>();
        }
        return new ArrayList<>(stateRequirements.getRequirementsBySchoolYear().keySet());
    }

    /**
     * Emits a single-line, grep-friendly pipeline stage marker with elapsed time
     * since upload. Format is intentionally flat key=value so it's easy to parse
     * with grep/awk without needing to correlate separate free-text log messages.
     */
    private void logStage(String stage, ExtractionJob job) {
        long elapsedMs = Duration.between(job.getUploadedAt(), ZonedDateTime.now()).toMillis();
        log.info("PIPELINE_STAGE={} jobId={} elapsedMs={}", stage, job.getId(), elapsedMs);
    }
}
