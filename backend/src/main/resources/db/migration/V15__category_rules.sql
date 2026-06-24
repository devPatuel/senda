-- Phase 15: auto-categorization rules. When an imported (or new) transaction's
-- description contains match_text (case-insensitive), the rule's category is
-- suggested. Cascades if the category is removed.

CREATE TABLE category_rules (
    id          BIGSERIAL    PRIMARY KEY,
    user_id     BIGINT       NOT NULL REFERENCES users (id),
    match_text  VARCHAR(100) NOT NULL,
    category_id BIGINT       NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_category_rules_user_match UNIQUE (user_id, match_text)
);

CREATE INDEX idx_category_rules_user ON category_rules (user_id);
