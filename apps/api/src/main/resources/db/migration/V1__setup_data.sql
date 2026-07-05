CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE users
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         TEXT        NOT NULL,
    display_name  TEXT        NOT NULL,
    password_hash TEXT        NOT NULL,
    avatar_url    TEXT,
    system_role   TEXT        NOT NULL,
    status        TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL,
    updated_at    TIMESTAMPTZ NOT NULL,
    deleted_at    TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;

CREATE TABLE auth_refresh_tokens
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    token_hash TEXT        NOT NULL,
    family_id  UUID        NOT NULL,
    status     TEXT        NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    rotated_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_ip TEXT,
    user_agent TEXT
);

CREATE UNIQUE INDEX uk_auth_refresh_tokens_token_hash ON auth_refresh_tokens (token_hash);

CREATE TABLE workspaces
(
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        TEXT        NOT NULL,
    name_lookup TEXT        NOT NULL,
    type        TEXT        NOT NULL,
    created_by  UUID        NOT NULL REFERENCES users (id),
    created_at  TIMESTAMPTZ NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL,
    deleted_at  TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_workspaces_name_lookup_active ON workspaces (name_lookup) WHERE deleted_at IS NULL;

CREATE TABLE workspace_members
(
    id           BIGSERIAL PRIMARY KEY,
    workspace_id UUID        NOT NULL REFERENCES workspaces (id),
    user_id      UUID        NOT NULL REFERENCES users (id),
    role         TEXT        NOT NULL,
    status       TEXT        NOT NULL,
    invited_by   UUID REFERENCES users (id),
    joined_at    TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (workspace_id, user_id)
);

CREATE TABLE projects
(
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    workspace_id UUID        NOT NULL REFERENCES workspaces (id),
    name         TEXT        NOT NULL,
    name_lookup  TEXT        NOT NULL,
    description  TEXT,
    created_by   UUID        NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    archived_at  TIMESTAMPTZ,
    deleted_at   TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_projects_workspace_name_lookup_active
    ON projects (workspace_id, name_lookup)
    WHERE deleted_at IS NULL;

CREATE TABLE dataset_schemas
(
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope         TEXT        NOT NULL,
    project_id    UUID REFERENCES projects (id),
    workspace_id  UUID REFERENCES workspaces (id),
    owner_user_id UUID REFERENCES users (id),
    version       INT         NOT NULL,
    name          TEXT        NOT NULL,
    created_by    UUID        NOT NULL REFERENCES users (id),
    created_at    TIMESTAMPTZ NOT NULL
);

CREATE INDEX ix_dataset_schemas_project ON dataset_schemas (project_id, version);

CREATE TABLE dataset_schema_columns
(
    id           BIGSERIAL PRIMARY KEY,
    schema_id    UUID        NOT NULL REFERENCES dataset_schemas (id),
    name         TEXT        NOT NULL,
    display_name TEXT,
    data_type    TEXT        NOT NULL,
    required     BOOLEAN     NOT NULL DEFAULT FALSE,
    description  TEXT,
    order_index  INT         NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL,
    UNIQUE (schema_id, name)
);

CREATE TABLE datasets
(
    id                 UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scope              TEXT        NOT NULL,
    workspace_id       UUID REFERENCES workspaces (id),
    project_id         UUID REFERENCES projects (id),
    owner_user_id      UUID REFERENCES users (id),
    current_version_id UUID,
    name               TEXT        NOT NULL,
    name_lookup        TEXT        NOT NULL,
    description        TEXT,
    source_type        TEXT,
    created_by         UUID        NOT NULL REFERENCES users (id),
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    deleted_at         TIMESTAMPTZ
);

CREATE UNIQUE INDEX uk_datasets_project_name_lookup_active
    ON datasets (project_id, name_lookup)
    WHERE deleted_at IS NULL AND project_id IS NOT NULL;

CREATE TABLE dataset_versions
(
    id                        UUID PRIMARY KEY     DEFAULT gen_random_uuid(),
    dataset_id                UUID        NOT NULL REFERENCES datasets (id),
    version                   INT         NOT NULL,
    schema_id                 UUID        NOT NULL REFERENCES dataset_schemas (id),
    schema_version            INT         NOT NULL,
    source_dataset_version_id UUID REFERENCES dataset_versions (id),
    base_version_id           UUID REFERENCES dataset_versions (id),
    import_mapping_json       JSONB,
    row_count                 INT         NOT NULL DEFAULT 0,
    status                    TEXT        NOT NULL,
    created_by                UUID        NOT NULL REFERENCES users (id),
    created_at                TIMESTAMPTZ NOT NULL,
    published_at              TIMESTAMPTZ,
    discarded_at              TIMESTAMPTZ,
    UNIQUE (dataset_id, version)
);

ALTER TABLE datasets
    ADD CONSTRAINT fk_datasets_current_version
        FOREIGN KEY (current_version_id) REFERENCES dataset_versions (id);

CREATE TABLE dataset_rows
(
    id                 BIGSERIAL PRIMARY KEY,
    dataset_version_id UUID        NOT NULL REFERENCES dataset_versions (id),
    row_index          INT         NOT NULL,
    cells_json         JSONB       NOT NULL,
    revision           INT         NOT NULL DEFAULT 0,
    row_hash           TEXT,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    UNIQUE (dataset_version_id, row_index)
);

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
