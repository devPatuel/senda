-- Phase 6: shopping list (grocery) and wishlist items.

CREATE TABLE shopping_items (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT         NOT NULL REFERENCES users (id),
    list_type       VARCHAR(10)    NOT NULL CHECK (list_type IN ('GROCERY', 'WISHLIST')),
    name            VARCHAR(100)   NOT NULL,
    estimated_price NUMERIC(14, 2),
    envelope_id     BIGINT REFERENCES allocation_envelopes (id) ON DELETE SET NULL,
    priority        INT,
    bought          BOOLEAN        NOT NULL DEFAULT FALSE,
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now()
);

CREATE INDEX idx_shopping_items_user_type ON shopping_items (user_id, list_type);
