-- Named groups of wishes. Deleting a list keeps its wishes: they just stop belonging to one.
CREATE TABLE wish_lists (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_wish_lists_user_name ON wish_lists (user_id, lower(name));

ALTER TABLE wishlist_items ADD COLUMN list_id BIGINT REFERENCES wish_lists (id) ON DELETE SET NULL;
CREATE INDEX idx_wishlist_items_list ON wishlist_items (list_id);
