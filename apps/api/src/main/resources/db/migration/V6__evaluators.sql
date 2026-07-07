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
