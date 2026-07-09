-- Phase 16: shared "couple" spaces. A space groups two (or more) users who share
-- accounts, categories and transactions (added in later waves). Membership starts
-- PENDING (invited) and becomes ACTIVE on acceptance.

CREATE TABLE spaces (
    id         BIGSERIAL     PRIMARY KEY,
    name       VARCHAR(100)  NOT NULL,
    created_by BIGINT        NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE space_members (
    id         BIGSERIAL    PRIMARY KEY,
    space_id   BIGINT       NOT NULL REFERENCES spaces (id) ON DELETE CASCADE,
    user_id    BIGINT       NOT NULL REFERENCES users (id),
    status     VARCHAR(10)  NOT NULL CHECK (status IN ('PENDING', 'ACTIVE')),
    joined_at  TIMESTAMPTZ,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_space_members_space_user UNIQUE (space_id, user_id)
);

-- Lookups by user_id (a user's memberships) need their own index; lookups by
-- space_id are already served by the leading column of the UNIQUE (space_id, user_id).
CREATE INDEX idx_space_members_user ON space_members (user_id);
