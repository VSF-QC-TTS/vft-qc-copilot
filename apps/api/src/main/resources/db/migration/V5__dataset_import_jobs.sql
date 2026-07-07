CREATE TABLE dataset_import_jobs
(
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    target_project_id UUID        NOT NULL REFERENCES projects (id),
    target_dataset_id UUID REFERENCES datasets (id),
    source_type       TEXT        NOT NULL,
    source_file_name  TEXT,
    status            TEXT        NOT NULL,
    mapping_json      JSONB,
    preview_json      JSONB,
    source_data_json  JSONB,
    error_message     TEXT,
    created_by        UUID        NOT NULL REFERENCES users (id),
    created_at        TIMESTAMPTZ NOT NULL,
    finished_at       TIMESTAMPTZ
);

CREATE INDEX ix_dataset_import_jobs_target_project_created
    ON dataset_import_jobs (target_project_id, created_at DESC);

ALTER TABLE dataset_versions
    ADD COLUMN import_job_id UUID REFERENCES dataset_import_jobs (id);
