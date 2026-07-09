-- Phase 17: shared-space scoping. A NULL space_id means the row is personal
-- (unchanged behaviour). A non-null space_id means it belongs to a couple space;
-- access is authorized by membership, not by user_id. Personal rows keep user_id
-- as owner; shared rows keep user_id as the author who recorded them.

ALTER TABLE categories   ADD COLUMN space_id BIGINT REFERENCES spaces (id);
ALTER TABLE accounts     ADD COLUMN space_id BIGINT REFERENCES spaces (id);
ALTER TABLE transactions ADD COLUMN space_id BIGINT REFERENCES spaces (id);

CREATE INDEX idx_categories_space   ON categories (space_id);
CREATE INDEX idx_accounts_space     ON accounts (space_id);
CREATE INDEX idx_transactions_space ON transactions (space_id);

-- Category name uniqueness now depends on scope: per-user for personal categories,
-- per-space for shared ones. The old whole-table UNIQUE cannot express that, so it
-- is replaced by two partial unique indexes.
ALTER TABLE categories DROP CONSTRAINT uq_categories_user_name_type;

CREATE UNIQUE INDEX uq_categories_user_name_type_personal
    ON categories (user_id, name, type) WHERE space_id IS NULL;

CREATE UNIQUE INDEX uq_categories_space_name_type
    ON categories (space_id, name, type) WHERE space_id IS NOT NULL;
