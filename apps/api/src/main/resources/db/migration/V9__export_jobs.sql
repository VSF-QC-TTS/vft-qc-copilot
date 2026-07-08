CREATE TABLE export_jobs
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_type           TEXT        NOT NULL,
    format             TEXT        NOT NULL,
    status             TEXT        NOT NULL,
    dataset_version_id UUID REFERENCES dataset_versions (id),
    test_run_id        UUID REFERENCES test_runs (id),
    result_mode        TEXT,
    file_path          TEXT,
    file_name          TEXT,
    file_size_bytes    BIGINT,
    error_code         TEXT,
    error_message      TEXT,
    created_by         UUID        NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    started_at         TIMESTAMPTZ,
    finished_at        TIMESTAMPTZ,
    expires_at         TIMESTAMPTZ,
    CONSTRAINT ck_export_jobs_dataset_source
        CHECK (
            (job_type = 'DATASET_VERSION' AND dataset_version_id IS NOT NULL AND test_run_id IS NULL)
                OR (job_type = 'TEST_RUN_RESULTS' AND test_run_id IS NOT NULL AND dataset_version_id IS NULL)
        )
);

CREATE INDEX ix_export_jobs_created_by_created
    ON export_jobs (created_by, created_at DESC);

CREATE INDEX ix_export_jobs_dataset_version_created
    ON export_jobs (dataset_version_id, created_at DESC);

CREATE INDEX ix_export_jobs_test_run_created
    ON export_jobs (test_run_id, created_at DESC);
