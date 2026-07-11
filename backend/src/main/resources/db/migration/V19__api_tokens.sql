-- Personal access tokens: long-lived, revocable API keys (one per device).
-- Only the SHA-256 hash is stored; the clear value is shown once on creation.
CREATE TABLE api_tokens (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT      NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash    VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex
    name          VARCHAR(80) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_used_at  TIMESTAMPTZ,
    revoked_at    TIMESTAMPTZ
);

CREATE INDEX idx_api_tokens_user ON api_tokens (user_id);
