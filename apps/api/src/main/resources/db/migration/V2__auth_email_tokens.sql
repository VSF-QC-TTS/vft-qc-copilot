CREATE TABLE auth_email_tokens
(
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id),
    token_hash TEXT        NOT NULL,
    purpose    TEXT        NOT NULL,
    status     TEXT        NOT NULL,
    issued_at  TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    created_ip TEXT,
    user_agent TEXT
);

CREATE UNIQUE INDEX uk_auth_email_tokens_token_hash ON auth_email_tokens (token_hash);

CREATE INDEX ix_auth_email_tokens_user_purpose_status
    ON auth_email_tokens (user_id, purpose, status);
