-- Phase 18: each member's net worth includes 50% of the balance of their couple
-- spaces' accounts. Persisted per snapshot so the history stays faithful. Existing
-- rows had no couple share, hence DEFAULT 0.

ALTER TABLE net_worth_snapshots
    ADD COLUMN couple_share NUMERIC(14, 2) NOT NULL DEFAULT 0;
