package com.immunization.validator;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.immunization.validator.controller.ExtractionController;
import com.immunization.validator.model.ExtractionJob;
import com.immunization.validator.model.ExtractionJobResponse;
import com.immunization.validator.model.ExtractionJobStatus;
import com.immunization.validator.repository.ExtractionJobRepository;
import com.immunization.validator.service.DocumentExtractionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockMultipartFile;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for ExtractionController, instantiated directly with mocked
 * dependencies rather than via @WebMvcTest -- this project's main class carries
 * an explicit @EnableJpaRepositories, which a web-slice test can't strip out
 * (it isn't an auto-configuration), so any @WebMvcTest here ends up demanding a
 * real DataSource/entityManagerFactory. Matches this repo's existing convention
 * of testing controllers/services via direct instantiation + Mockito rather
 * than booting Spring (see ValidationServiceTest).
 */
class ExtractionControllerTest {

    @Mock
    private DocumentExtractionService documentExtractionService;

    @Mock
    private ExtractionJobRepository extractionJobRepository;

    private ExtractionController controller;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        controller = new ExtractionController(documentExtractionService, extractionJobRepository, objectMapper);
    }

    @Test
    @DisplayName("submit() returns jobId and status immediately")
    void testSubmit_ReturnsImmediately() {
        UUID jobId = UUID.randomUUID();
        ExtractionJob job = ExtractionJob.builder()
                .id(jobId).status(ExtractionJobStatus.SUBMITTED)
                .uploadedAt(ZonedDateTime.now()).build();

        when(documentExtractionService.submitJob(any())).thenReturn(job);

        MockMultipartFile file = new MockMultipartFile("file", "summary.pdf", "application/pdf", "content".getBytes());

        ResponseEntity<ExtractionJobResponse> response = controller.submit(file, false);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(jobId.toString(), response.getBody().getJobId());
        assertEquals(ExtractionJobStatus.SUBMITTED, response.getBody().getStatus());
        assertNull(response.getBody().getValidationResult());
        assertNull(response.getBody().getErrorMessage());
        verify(documentExtractionService).pollAndProcess(jobId);
    }

    @Test
    @DisplayName("submit() with an empty file returns 400 without creating a job")
    void testSubmit_EmptyFile_Returns400() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "empty.pdf", "application/pdf", new byte[0]);

        ResponseEntity<ExtractionJobResponse> response = controller.submit(emptyFile, false);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        verifyNoInteractions(documentExtractionService);
    }

    @Test
    @DisplayName("submit() does not kick off polling for a job that already FAILED at submission")
    void testSubmit_SkipsPollWhenAlreadyFailed() {
        UUID jobId = UUID.randomUUID();
        ExtractionJob job = ExtractionJob.builder()
                .id(jobId).status(ExtractionJobStatus.FAILED)
                .errorMessageUser("LLAMA_CLOUD_API_KEY is not configured")
                .uploadedAt(ZonedDateTime.now()).build();

        when(documentExtractionService.submitJob(any())).thenReturn(job);

        MockMultipartFile file = new MockMultipartFile("file", "summary.pdf", "application/pdf", "content".getBytes());

        ResponseEntity<ExtractionJobResponse> response = controller.submit(file, false);

        assertEquals(ExtractionJobStatus.FAILED, response.getBody().getStatus());
        assertEquals("LLAMA_CLOUD_API_KEY is not configured", response.getBody().getErrorMessage());
        verify(documentExtractionService, never()).pollAndProcess(any());
    }

    @Test
    @DisplayName("submit(wait=true) long-polls until a terminal status and returns validationResult")
    void testSubmit_Wait_ReturnsOnceTerminal() {
        UUID jobId = UUID.randomUUID();
        ExtractionJob submitted = ExtractionJob.builder()
                .id(jobId).status(ExtractionJobStatus.SUBMITTED)
                .uploadedAt(ZonedDateTime.now()).build();
        ExtractionJob completed = ExtractionJob.builder()
                .id(jobId).status(ExtractionJobStatus.COMPLETED)
                .validationResult("{\"preschool\":{\"status\":\"VALID\"}}")
                .uploadedAt(ZonedDateTime.now()).build();

        when(documentExtractionService.submitJob(any())).thenReturn(submitted);
        when(extractionJobRepository.findById(jobId)).thenReturn(Optional.of(completed));

        MockMultipartFile file = new MockMultipartFile("file", "summary.pdf", "application/pdf", "content".getBytes());

        ResponseEntity<ExtractionJobResponse> response = controller.submit(file, true);

        assertEquals(ExtractionJobStatus.COMPLETED, response.getBody().getStatus());
        assertEquals("VALID", response.getBody().getValidationResult().get("preschool").get("status").asText());
    }

    @Test
    @DisplayName("getStatus() for a FAILED job returns the error message")
    void testGetStatus_Failed() {
        UUID jobId = UUID.randomUUID();
        ExtractionJob failed = ExtractionJob.builder()
                .id(jobId).status(ExtractionJobStatus.FAILED)
                .errorMessageUser("LlamaCloud reported job failure")
                .uploadedAt(ZonedDateTime.now()).build();

        when(extractionJobRepository.findById(jobId)).thenReturn(Optional.of(failed));

        ResponseEntity<ExtractionJobResponse> response = controller.getStatus(jobId);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(ExtractionJobStatus.FAILED, response.getBody().getStatus());
        assertEquals("LlamaCloud reported job failure", response.getBody().getErrorMessage());
        assertNull(response.getBody().getValidationResult());
    }

    @Test
    @DisplayName("getStatus() for an unknown job returns 404")
    void testGetStatus_NotFound() {
        UUID jobId = UUID.randomUUID();
        when(extractionJobRepository.findById(jobId)).thenReturn(Optional.empty());

        ResponseEntity<ExtractionJobResponse> response = controller.getStatus(jobId);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
    }
}
