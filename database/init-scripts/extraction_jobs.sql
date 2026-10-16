-- LlamaExtract async ingestion job tracking table.
-- Runs only against a fresh postgres_data volume (docker-entrypoint-initdb.d
-- convention) -- same mechanism used to load vaccines.sql.

CREATE TABLE IF NOT EXISTS extraction_jobs (
    id UUID PRIMARY KEY,
    llama_extract_job_id TEXT,
    status TEXT NOT NULL,
    source_file_name TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    result_json JSONB,
    error_message_internal TEXT,
    error_message_user TEXT,
    validation_result JSONB,
    patient_identifier TEXT,
    next_poll_at TIMESTAMP WITH TIME ZONE,
    poll_attempts INTEGER NOT NULL DEFAULT 0,
    last_polled_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_extraction_jobs_status ON extraction_jobs(status);

COMMENT ON TABLE extraction_jobs IS 'Tracks async LlamaExtract document ingestion jobs (PENDING -> SUBMITTED -> PROCESSING -> COMPLETED/FAILED)';
COMMENT ON COLUMN extraction_jobs.llama_extract_job_id IS 'Job id returned by LlamaCloud; null until submitted';
COMMENT ON COLUMN extraction_jobs.result_json IS 'Raw LlamaExtract output, stored once the job completes';
COMMENT ON COLUMN extraction_jobs.error_message_internal IS 'Full technical failure detail (exception message, upstream error body) -- for logs/support, never returned via the API';
COMMENT ON COLUMN extraction_jobs.error_message_user IS 'Sanitized failure message safe to return via the API';
COMMENT ON COLUMN extraction_jobs.validation_result IS 'Map of schoolYear -> ValidationResponse produced by re-running the mapped immunizations through ValidationService';
COMMENT ON COLUMN extraction_jobs.patient_identifier IS 'Best-effort patient identifier from the extracted document, for later linking to a student record';
COMMENT ON COLUMN extraction_jobs.next_poll_at IS 'Reserved for future per-job poll backoff; no scheduler reads or writes this column yet';
COMMENT ON COLUMN extraction_jobs.poll_attempts IS 'Reserved for future per-job poll backoff; no scheduler reads or writes this column yet';
COMMENT ON COLUMN extraction_jobs.last_polled_at IS 'Reserved for future per-job poll backoff; no scheduler reads or writes this column yet';
