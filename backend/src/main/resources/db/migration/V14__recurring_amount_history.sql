-- Phase 14: amount history for recurring payments. A row is written on creation
-- (the baseline) and on every amount change, so the UI can show the last price
-- variation ("▲ +X%"). Cascades when the payment is deleted.

CREATE TABLE recurring_amount_history (
    id           BIGSERIAL      PRIMARY KEY,
    recurring_id BIGINT         NOT NULL REFERENCES recurring_payments (id) ON DELETE CASCADE,
    amount       NUMERIC(12, 2) NOT NULL,
    changed_at   TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_recurring_amount_history_recurring ON recurring_amount_history (recurring_id, changed_at);
