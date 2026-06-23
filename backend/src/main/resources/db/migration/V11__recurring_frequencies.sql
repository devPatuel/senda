-- Phase 11: weekly and quarterly recurring frequencies.
-- WEEKLY uses a new day_of_week (1=Monday..7=Sunday, ISO). QUARTERLY reuses
-- month + day_of_month and recurs every 3 months. day_of_month stays NOT NULL
-- (weekly payments store a placeholder 1, ignored by the forecast).

-- Widen the frequency CHECK to the four supported values. The inline column
-- check from V9 is named by Postgres as <table>_<column>_check.
ALTER TABLE recurring_payments DROP CONSTRAINT recurring_payments_frequency_check;
ALTER TABLE recurring_payments
    ADD CONSTRAINT recurring_payments_frequency_check
    CHECK (frequency IN ('MONTHLY', 'ANNUAL', 'WEEKLY', 'QUARTERLY'));

-- ISO day of week for weekly payments; null for the other frequencies.
ALTER TABLE recurring_payments
    ADD COLUMN day_of_week INT CHECK (day_of_week IS NULL OR day_of_week BETWEEN 1 AND 7);
