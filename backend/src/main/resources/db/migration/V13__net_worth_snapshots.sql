-- Phase 13: daily net-worth snapshots. Written lazily once per day when the user
-- loads their net worth, so the history accumulates without a scheduler. One row
-- per (user, day) enforced by a unique constraint.

CREATE TABLE net_worth_snapshots (
    id             BIGSERIAL      PRIMARY KEY,
    user_id        BIGINT         NOT NULL REFERENCES users (id),
    snapshot_date  DATE           NOT NULL,
    net            NUMERIC(14, 2) NOT NULL,
    liquid         NUMERIC(14, 2) NOT NULL,
    investments    NUMERIC(14, 2) NOT NULL,
    debts_in_favor NUMERIC(14, 2) NOT NULL,
    debts_against  NUMERIC(14, 2) NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL DEFAULT now(),
    CONSTRAINT uq_net_worth_snapshots_user_date UNIQUE (user_id, snapshot_date)
);

CREATE INDEX idx_net_worth_snapshots_user_date ON net_worth_snapshots (user_id, snapshot_date);
