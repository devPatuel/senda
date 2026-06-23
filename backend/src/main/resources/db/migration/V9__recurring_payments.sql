-- Phase 9: recurring payments (subscriptions, insurance...). Forecast only —
-- they never create transactions. The next due date is derived from
-- day_of_month/month at read time, so nothing here stores it.

CREATE TABLE recurring_payments (
    id           BIGSERIAL       PRIMARY KEY,
    user_id      BIGINT          NOT NULL REFERENCES users (id),
    name         VARCHAR(100)    NOT NULL,
    amount       NUMERIC(12, 2)  NOT NULL CHECK (amount > 0),
    frequency    VARCHAR(10)     NOT NULL CHECK (frequency IN ('MONTHLY', 'ANNUAL')),
    category_id  BIGINT          NOT NULL REFERENCES categories (id),
    day_of_month INT             NOT NULL CHECK (day_of_month BETWEEN 1 AND 31),
    month        INT             CHECK (month BETWEEN 1 AND 12),
    created_at   TIMESTAMPTZ     NOT NULL DEFAULT now()
);

CREATE INDEX idx_recurring_payments_user ON recurring_payments (user_id);
