-- Phase 1: accounts (liquid money). Balance is updated manually by the user.

CREATE TABLE accounts (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT         NOT NULL REFERENCES users (id),
    name       VARCHAR(100)   NOT NULL,
    type       VARCHAR(10)    NOT NULL CHECK (type IN ('BANK', 'CASH')),
    balance    NUMERIC(14, 2) NOT NULL DEFAULT 0,
    currency   VARCHAR(3)     NOT NULL DEFAULT 'EUR',
    archived   BOOLEAN        NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_accounts_user ON accounts (user_id);
