-- Phase 2: Debt tracking (debts + payments)
CREATE TABLE debts (
    id             BIGSERIAL PRIMARY KEY,
    user_id        BIGINT       NOT NULL REFERENCES users (id),
    direction      VARCHAR(12)  NOT NULL CHECK (direction IN ('THEY_OWE_ME', 'I_OWE')),
    counterparty   VARCHAR(100) NOT NULL,
    concept        VARCHAR(255) NOT NULL,
    original_amount NUMERIC(14, 2) NOT NULL CHECK (original_amount > 0),
    date           DATE         NOT NULL,
    settled        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_debts_user_id ON debts (user_id);

CREATE TABLE debt_payments (
    id         BIGSERIAL PRIMARY KEY,
    debt_id    BIGINT         NOT NULL REFERENCES debts (id) ON DELETE CASCADE,
    user_id    BIGINT         NOT NULL REFERENCES users (id),
    amount     NUMERIC(14, 2) NOT NULL CHECK (amount > 0),
    date       DATE           NOT NULL,
    note       VARCHAR(255),
    created_at TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_debt_payments_debt_id ON debt_payments (debt_id);
