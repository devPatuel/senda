-- Phase 5: allocation envelopes (salary split / envelope budgeting).
-- Percentages use NUMERIC(5,2); fiat balances use NUMERIC(14,2).

CREATE TABLE allocation_envelopes (
    id         BIGSERIAL       PRIMARY KEY,
    user_id    BIGINT          NOT NULL REFERENCES users (id),
    name       VARCHAR(100)    NOT NULL,
    percentage NUMERIC(5, 2)   NOT NULL CHECK (percentage >= 0 AND percentage <= 100),
    position   INT             NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_allocation_envelopes_user ON allocation_envelopes (user_id);

CREATE TABLE envelope_balances (
    id          BIGSERIAL       PRIMARY KEY,
    envelope_id BIGINT          NOT NULL UNIQUE REFERENCES allocation_envelopes (id) ON DELETE CASCADE,
    user_id     BIGINT          NOT NULL REFERENCES users (id),
    balance     NUMERIC(14, 2)  NOT NULL DEFAULT 0
);

CREATE INDEX idx_envelope_balances_user ON envelope_balances (user_id);
