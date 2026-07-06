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
