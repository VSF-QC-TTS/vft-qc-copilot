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
