-- Sells reduce a holding's quantity and keep its average cost (see ADR 0008).
-- The inline CHECK added in V30 got Postgres' default name.
ALTER TABLE holding_lots DROP CONSTRAINT holding_lots_kind_check;
ALTER TABLE holding_lots ADD CONSTRAINT holding_lots_kind_check
    CHECK (kind IN ('BUY', 'REWARD', 'SELL'));
