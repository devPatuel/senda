-- Phase 21 (Feature 7): food product catalog + per-supermarket price history.
-- Shared base for the price manager (F7) and the shopping list (F10).
-- space_id is nullable: NULL = personal (F7 scope); a future non-null value means
-- the product belongs to a couple space (F10), authorized by membership.

CREATE TABLE products (
    id         BIGSERIAL     PRIMARY KEY,
    user_id    BIGINT        NOT NULL REFERENCES users (id),
    space_id   BIGINT        REFERENCES spaces (id),
    name       VARCHAR(100)  NOT NULL,
    unit_type  VARCHAR(10)   NOT NULL CHECK (unit_type IN ('WEIGHT', 'QUANTITY')),
    amount     NUMERIC(12, 3) NOT NULL,
    unit       VARCHAR(20)   NOT NULL,
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Product name is unique per scope: per-user for personal products, per-space
-- for shared ones (two partial unique indexes, same shape as V17 categories).
CREATE UNIQUE INDEX uq_products_user_name_personal
    ON products (user_id, name) WHERE space_id IS NULL;

CREATE UNIQUE INDEX uq_products_space_name
    ON products (space_id, name) WHERE space_id IS NOT NULL;

CREATE INDEX idx_products_space ON products (space_id);

-- Price history. A row belongs to a product; scope is inherited from it
-- (no user_id/space_id here). Deleting a product removes its price history.
CREATE TABLE price_entries (
    id          BIGSERIAL     PRIMARY KEY,
    product_id  BIGINT        NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    price       NUMERIC(14, 2) NOT NULL,
    supermarket VARCHAR(80)   NOT NULL,
    recorded_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

-- Serves both "history of a product" and "latest price per supermarket".
CREATE INDEX idx_price_entries_product_super_time
    ON price_entries (product_id, supermarket, recorded_at DESC);
