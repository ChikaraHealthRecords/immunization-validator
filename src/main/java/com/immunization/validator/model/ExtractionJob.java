package com.immunization.validator.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.ZonedDateTime;
import java.util.UUID;

/**
 * JPA Entity tracking an async LlamaExtract document ingestion job.
 *
 * Lifecycle: PENDING -> SUBMITTED -> PROCESSING -> COMPLETED / FAILED.
 * See {@link ExtractionJobStatus}.
 */
@Entity
@Table(name = "extraction_jobs")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractionJob {

    @Id
    @Column(name = "id")
    private UUID id;

    @Column(name = "llama_extract_job_id")
    private String llamaExtractJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ExtractionJobStatus status;

    @Column(name = "source_file_name")
    private String sourceFileName;

    @Column(name = "uploaded_at", nullable = false)
    private ZonedDateTime uploadedAt;

    /** Raw LlamaExtract output, stored once the job completes. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private String resultJson;

    /** Full technical detail (exception message, upstream error body) -- for logs/support, never returned via the API. */
    @Column(name = "error_message_internal", columnDefinition = "TEXT")
    private String errorMessageInternal;

    /** Sanitized message safe to return to API callers. */
    @Column(name = "error_message_user", columnDefinition = "TEXT")
    private String errorMessageUser;

    /** Map of schoolYear -> ValidationResponse, serialized as JSON. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "validation_result", columnDefinition = "jsonb")
    private String validationResult;

    @Column(name = "patient_identifier")
    private String patientIdentifier;

    /** Reserved for future per-job poll backoff; not yet read or written by any scheduler. */
    @Column(name = "next_poll_at")
    private ZonedDateTime nextPollAt;

    /** Reserved for future per-job poll backoff; not yet read or written by any scheduler. */
    @Builder.Default
    @Column(name = "poll_attempts", nullable = false)
    private int pollAttempts = 0;

    /** Reserved for future per-job poll backoff; not yet read or written by any scheduler. */
    @Column(name = "last_polled_at")
    private ZonedDateTime lastPolledAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
