-- Phase 8: unify envelope budgeting into expense categories.
-- Each expense category becomes an envelope with a persisted balance, and the
-- allocation plan lives as a target_percentage on the category itself.

-- Per-expense-category persisted balance (the "envelope"). 1-to-1 with the category.
CREATE TABLE category_balances (
    id          BIGSERIAL       PRIMARY KEY,
    category_id BIGINT          NOT NULL UNIQUE REFERENCES categories (id) ON DELETE CASCADE,
    user_id     BIGINT          NOT NULL REFERENCES users (id),
    balance     NUMERIC(14, 2)  NOT NULL DEFAULT 0
);

CREATE INDEX idx_category_balances_user ON category_balances (user_id);

-- Target percentage for the salary split (allocation plan). NULL = not in the plan.
ALTER TABLE categories
    ADD COLUMN target_percentage NUMERIC(5, 2)
        CHECK (target_percentage IS NULL OR (target_percentage >= 0 AND target_percentage <= 100));

-- Backfill a zero balance for every existing expense category.
INSERT INTO category_balances (category_id, user_id, balance)
SELECT id, user_id, 0 FROM categories WHERE type = 'EXPENSE';

-- Shopping items referenced the old envelopes; point them at categories instead
-- (the category IS the envelope now). Old envelope ids no longer map to anything,
-- so clear them. The column keeps its name to limit churn.
ALTER TABLE shopping_items DROP CONSTRAINT shopping_items_envelope_id_fkey;
UPDATE shopping_items SET envelope_id = NULL;
ALTER TABLE shopping_items
    ADD CONSTRAINT shopping_items_envelope_id_fkey
    FOREIGN KEY (envelope_id) REFERENCES categories (id) ON DELETE SET NULL;

-- The allocation module now operates on categories; drop the standalone envelopes.
DROP TABLE envelope_balances;
DROP TABLE allocation_envelopes;
