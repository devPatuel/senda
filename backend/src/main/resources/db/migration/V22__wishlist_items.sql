-- Feature 9: wishlist moves out of shopping_items into its own richer model
-- (image, product link, comment, price). Personal scope for now (space_id NULL);
-- space_id is reserved for the future shared-couple variant.
CREATE TABLE wishlist_items (
    id          BIGSERIAL   PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    space_id    BIGINT      REFERENCES spaces (id) ON DELETE CASCADE,
    name        VARCHAR(120) NOT NULL,
    image_url   VARCHAR(1000),
    product_url VARCHAR(1000),
    comment     VARCHAR(1000),
    price       NUMERIC(14, 2),
    priority    INT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_wishlist_items_user ON wishlist_items (user_id);

-- Migrate existing WISHLIST rows from the old shopping module (F9 supersedes them).
INSERT INTO wishlist_items (user_id, name, comment, price, priority, created_at)
SELECT user_id, name, notes, estimated_price, priority, created_at
FROM shopping_items
WHERE list_type = 'WISHLIST';

-- Remove migrated rows so the old module keeps only GROCERY until it is retired (V24).
DELETE FROM shopping_items WHERE list_type = 'WISHLIST';
