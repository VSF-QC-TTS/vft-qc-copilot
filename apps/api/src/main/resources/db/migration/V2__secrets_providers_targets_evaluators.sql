CREATE TABLE secrets
(
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope           TEXT        NOT NULL,
    owner_user_id   UUID REFERENCES users (id),
    workspace_id    UUID REFERENCES workspaces (id),
    name            TEXT        NOT NULL,
    name_lookup     TEXT        NOT NULL,
    encrypted_value TEXT        NOT NULL,
    masked_preview  TEXT,
    status          TEXT        NOT NULL,
    created_by      UUID        NOT NULL REFERENCES users (id),
    created_at      TIMESTAMPTZ NOT NULL,
    updated_at      TIMESTAMPTZ NOT NULL,
    deleted_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_secrets_workspace_name_lookup_active
    ON secrets (workspace_id, name_lookup)
    WHERE deleted_at IS NULL AND workspace_id IS NOT NULL;

CREATE UNIQUE INDEX uk_secrets_owner_name_lookup_active
    ON secrets (owner_user_id, name_lookup)
    WHERE deleted_at IS NULL AND owner_user_id IS NOT NULL;

CREATE TABLE llm_providers
(
    id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope                 TEXT        NOT NULL,
    owner_user_id         UUID REFERENCES users (id),
    workspace_id          UUID REFERENCES workspaces (id),
    name                  TEXT        NOT NULL,
    name_lookup           TEXT        NOT NULL,
    provider_type         TEXT        NOT NULL,
    secret_id             UUID REFERENCES secrets (id),
    base_url              TEXT,
    default_model         TEXT,
    available_models_json JSONB,
    config_json           JSONB,
    status                TEXT        NOT NULL,
    created_by            UUID        NOT NULL REFERENCES users (id),
    created_at            TIMESTAMPTZ NOT NULL,
    updated_at            TIMESTAMPTZ NOT NULL,
    deleted_at            TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_llm_providers_workspace_name_lookup_active
    ON llm_providers (workspace_id, name_lookup)
    WHERE deleted_at IS NULL AND workspace_id IS NOT NULL;

CREATE UNIQUE INDEX uk_llm_providers_owner_name_lookup_active
    ON llm_providers (owner_user_id, name_lookup)
    WHERE deleted_at IS NULL AND owner_user_id IS NOT NULL;

CREATE TABLE llm_provider_test_logs
(
    id                     BIGSERIAL PRIMARY KEY,
    llm_provider_id        UUID        NOT NULL REFERENCES llm_providers (id),
    status                 TEXT        NOT NULL,
    model                  TEXT,
    request_json_sanitized JSONB,
    response_preview       TEXT,
    error_code             TEXT,
    error_message          TEXT,
    latency_ms             INT,
    token_usage_json       JSONB,
    tested_by              UUID        NOT NULL REFERENCES users (id),
    tested_at              TIMESTAMPTZ NOT NULL,
    created_at             TIMESTAMPTZ NOT NULL
);

CREATE TABLE chatbot_targets
(
    id                              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id                      UUID        NOT NULL REFERENCES projects (id),
    name                            TEXT        NOT NULL,
    name_lookup                     TEXT        NOT NULL,
    description                     TEXT,
    raw_curl_sanitized              TEXT,
    raw_curl_hash                   TEXT,
    method                          TEXT,
    url                             TEXT,
    headers_json                    JSONB,
    query_params_json               JSONB,
    body_json                       JSONB,
    auth_config_json                JSONB,
    dataset_mapping_json            JSONB,
    latest_example_id               BIGINT,
    response_extraction_config_json JSONB,
    status                          TEXT        NOT NULL,
    created_by                      UUID        NOT NULL REFERENCES users (id),
    created_at                      TIMESTAMPTZ NOT NULL,
    updated_at                      TIMESTAMPTZ NOT NULL,
    deleted_at                      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_chatbot_targets_project_name_lookup_active
    ON chatbot_targets (project_id, name_lookup)
    WHERE deleted_at IS NULL;

CREATE TABLE chatbot_target_examples
(
    id                    BIGSERIAL PRIMARY KEY,
    chatbot_target_id     UUID        NOT NULL REFERENCES chatbot_targets (id),
    request_json          JSONB,
    response_status_code  INT,
    response_headers_json JSONB,
    response_body_json    JSONB,
    extracted_output_json JSONB,
    latency_ms            INT,
    error_message         TEXT,
    tested_by             UUID        NOT NULL REFERENCES users (id),
    created_at            TIMESTAMPTZ NOT NULL
);

ALTER TABLE chatbot_targets
    ADD CONSTRAINT fk_chatbot_targets_latest_example
        FOREIGN KEY (latest_example_id) REFERENCES chatbot_target_examples (id);

CREATE TABLE evaluators
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id  UUID        NOT NULL REFERENCES projects (id),
    name        TEXT        NOT NULL,
    name_lookup TEXT        NOT NULL,
    type        TEXT        NOT NULL,
    created_by  UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_evaluators_project_name_lookup_active
    ON evaluators (project_id, name_lookup)
    WHERE deleted_at IS NULL;

CREATE TABLE evaluator_versions
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evaluator_id UUID        NOT NULL REFERENCES evaluators (id),
    version     INT         NOT NULL,
    config_json JSONB,
    config_hash TEXT        NOT NULL,
    created_by  UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    UNIQUE (evaluator_id, version)
);

CREATE INDEX ix_evaluator_versions_evaluator_version
    ON evaluator_versions (evaluator_id, version DESC);

CREATE TABLE evaluator_rules
(
    id                   BIGSERIAL PRIMARY KEY,
    evaluator_version_id UUID        NOT NULL REFERENCES evaluator_versions (id),
    field_source         TEXT        NOT NULL,
    operator             TEXT        NOT NULL,
    expected_value_json  JSONB,
    weight               NUMERIC(8, 4),
    required             BOOLEAN     NOT NULL DEFAULT FALSE,
    order_index          INT         NOT NULL,
    config_json          JSONB,
    created_at           TIMESTAMPTZ NOT NULL,
    updated_at           TIMESTAMPTZ NOT NULL,
    UNIQUE (evaluator_version_id, order_index)
);
