-- Feature 10: shopping list built on top of the food catalog (F7). Each row marks
-- a catalog product as "to buy". Personal scope now (space_id NULL, reserved for
-- the future shared-couple variant). One product appears at most once per user.
CREATE TABLE shopping_list_items (
    id         BIGSERIAL   PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    space_id   BIGINT      REFERENCES spaces (id) ON DELETE CASCADE,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    quantity   INT         NOT NULL DEFAULT 1 CHECK (quantity > 0),
    checked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_shopping_list_user_product UNIQUE (user_id, product_id)
);

CREATE INDEX idx_shopping_list_items_user ON shopping_list_items (user_id);
