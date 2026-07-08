CREATE TABLE test_runs
(
    id                          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                  UUID        NOT NULL REFERENCES projects (id),
    run_type                    TEXT        NOT NULL,
    dataset_id                  UUID        NOT NULL REFERENCES datasets (id),
    dataset_version_id          UUID        NOT NULL REFERENCES dataset_versions (id),
    dataset_schema_id           UUID        NOT NULL REFERENCES dataset_schemas (id),
    dataset_schema_version      INT         NOT NULL,
    judge_provider_id           UUID REFERENCES llm_providers (id),
    judge_model                 TEXT,
    status                      TEXT        NOT NULL,
    progress_completed          INT         NOT NULL DEFAULT 0,
    progress_total              INT         NOT NULL DEFAULT 0,
    summary_metrics_json        JSONB,
    domain_config_snapshot_json JSONB,
    promptfoo_config_snapshot_json JSONB,
    domain_config_hash          TEXT        NOT NULL,
    promptfoo_config_hash       TEXT        NOT NULL,
    started_by                  UUID        NOT NULL REFERENCES users (id),
    started_at                  TIMESTAMPTZ,
    finished_at                 TIMESTAMPTZ,
    cancel_requested_at         TIMESTAMPTZ,
    created_at                  TIMESTAMPTZ NOT NULL,
    updated_at                  TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_test_runs_project_created
    ON test_runs (project_id, created_at DESC);

CREATE INDEX ix_test_runs_project_status
    ON test_runs (project_id, status);

CREATE TABLE test_run_targets
(
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_run_id       UUID        NOT NULL REFERENCES test_runs (id),
    target_type       TEXT        NOT NULL,
    chatbot_target_id UUID REFERENCES chatbot_targets (id),
    provider_id       UUID REFERENCES llm_providers (id),
    model             TEXT,
    name              TEXT        NOT NULL,
    order_index       INT         NOT NULL,
    config_json       JSONB,
    created_at        TIMESTAMPTZ NOT NULL,
    UNIQUE (test_run_id, order_index)
);

CREATE INDEX ix_test_run_targets_run
    ON test_run_targets (test_run_id, order_index);

CREATE TABLE test_run_evaluator_versions
(
    id                   BIGSERIAL PRIMARY KEY,
    test_run_id          UUID        NOT NULL REFERENCES test_runs (id),
    evaluator_id         UUID        NOT NULL REFERENCES evaluators (id),
    evaluator_version_id UUID        NOT NULL REFERENCES evaluator_versions (id),
    evaluator_type       TEXT        NOT NULL,
    version              INT         NOT NULL,
    order_index          INT         NOT NULL,
    config_json          JSONB,
    rules_json           JSONB,
    created_at           TIMESTAMPTZ NOT NULL,
    UNIQUE (test_run_id, evaluator_version_id),
    UNIQUE (test_run_id, order_index)
);

CREATE INDEX ix_test_run_evaluator_versions_run
    ON test_run_evaluator_versions (test_run_id, order_index);

CREATE TABLE test_case_results
(
    id                     BIGSERIAL PRIMARY KEY,
    test_run_id            UUID        NOT NULL REFERENCES test_runs (id),
    test_run_target_id     UUID        NOT NULL REFERENCES test_run_targets (id),
    dataset_row_id         BIGINT      NOT NULL REFERENCES dataset_rows (id),
    status                 TEXT        NOT NULL,
    score                  NUMERIC(8, 4),
    passed                 BOOLEAN,
    error_category         TEXT,
    request_json           JSONB,
    response_json          JSONB,
    extracted_output_json  JSONB,
    grading_result_json    JSONB,
    component_results_json JSONB,
    token_usage_json       JSONB,
    latency_ms             INT,
    machine_summary        TEXT,
    created_at             TIMESTAMPTZ NOT NULL,
    updated_at             TIMESTAMPTZ NOT NULL,
    UNIQUE (test_run_id, test_run_target_id, dataset_row_id)
);

CREATE INDEX ix_test_case_results_run_row
    ON test_case_results (test_run_id, dataset_row_id);

CREATE INDEX ix_test_case_results_target
    ON test_case_results (test_run_target_id);

CREATE TABLE test_case_result_reviews
(
    id                  BIGSERIAL PRIMARY KEY,
    test_case_result_id BIGINT      NOT NULL REFERENCES test_case_results (id),
    reviewer_id         UUID        NOT NULL REFERENCES users (id),
    review_status       TEXT        NOT NULL,
    override_status     TEXT,
    override_score      NUMERIC(8, 4),
    note                TEXT,
    created_at          TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    UNIQUE (test_case_result_id)
);

CREATE INDEX ix_test_case_result_reviews_reviewer
    ON test_case_result_reviews (reviewer_id, updated_at DESC);

CREATE TABLE runner_instances
(
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    runner_key          TEXT        NOT NULL,
    hostname            TEXT,
    version             TEXT,
    status              TEXT        NOT NULL,
    current_test_run_id UUID REFERENCES test_runs (id),
    current_command_id  UUID,
    last_heartbeat_at   TIMESTAMPTZ NOT NULL,
    registered_at       TIMESTAMPTZ NOT NULL,
    updated_at          TIMESTAMPTZ NOT NULL,
    UNIQUE (runner_key)
);

CREATE INDEX ix_runner_instances_status_heartbeat
    ON runner_instances (status, last_heartbeat_at DESC);

CREATE INDEX ix_runner_instances_current_run
    ON runner_instances (current_test_run_id);

CREATE TABLE runner_commands
(
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    test_run_id      UUID        NOT NULL REFERENCES test_runs (id),
    runner_id        UUID REFERENCES runner_instances (id),
    command_type     TEXT        NOT NULL,
    idempotency_key  TEXT        NOT NULL,
    stream_id        TEXT,
    status           TEXT        NOT NULL,
    attempts         INT         NOT NULL DEFAULT 0,
    sanitized_error  TEXT,
    created_at       TIMESTAMPTZ NOT NULL,
    published_at     TIMESTAMPTZ,
    acknowledged_at  TIMESTAMPTZ,
    updated_at       TIMESTAMPTZ NOT NULL,
    UNIQUE (idempotency_key)
);

ALTER TABLE runner_instances
    ADD CONSTRAINT fk_runner_instances_current_command
        FOREIGN KEY (current_command_id) REFERENCES runner_commands (id);

CREATE INDEX ix_runner_commands_status_created
    ON runner_commands (status, created_at);

CREATE INDEX ix_runner_commands_run
    ON runner_commands (test_run_id, created_at);

CREATE INDEX ix_runner_commands_stream_id
    ON runner_commands (stream_id);

CREATE TABLE runner_events
(
    id              BIGSERIAL PRIMARY KEY,
    event_id        TEXT        NOT NULL,
    test_run_id     UUID        NOT NULL REFERENCES test_runs (id),
    runner_id       UUID REFERENCES runner_instances (id),
    command_id      UUID REFERENCES runner_commands (id),
    event_type      TEXT        NOT NULL,
    sequence_no     BIGINT      NOT NULL,
    stream_id       TEXT,
    status          TEXT        NOT NULL,
    attempts        INT         NOT NULL DEFAULT 0,
    payload_json    JSONB,
    sanitized_error TEXT,
    occurred_at     TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    UNIQUE (event_id),
    UNIQUE (test_run_id, sequence_no)
);

CREATE INDEX ix_runner_events_run_sequence
    ON runner_events (test_run_id, sequence_no);

CREATE INDEX ix_runner_events_status_created
    ON runner_events (status, created_at);

CREATE INDEX ix_runner_events_stream_id
    ON runner_events (stream_id);
