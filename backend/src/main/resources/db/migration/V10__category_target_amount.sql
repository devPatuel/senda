-- Phase 10: optional spending target per expense-category envelope. The target
-- is the amount the user wants to keep funded in the envelope; the UI renders a
-- balance-vs-target progress bar. Null means no target set.

ALTER TABLE category_balances
    ADD COLUMN target_amount NUMERIC(14, 2)
        CHECK (target_amount IS NULL OR target_amount >= 0);
