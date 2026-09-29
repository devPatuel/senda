-- Distinguishes bought lots from rewards (staking, interest) so the UI can show
-- how much of the invested amount came from rewards. Existing lots are buys.
ALTER TABLE holding_lots
    ADD COLUMN kind VARCHAR(10) NOT NULL DEFAULT 'BUY'
        CHECK (kind IN ('BUY', 'REWARD'));
