-- Starting point of the personal discrepancy: the real total on the day the user
-- turned it on, so older gaps are not dragged along. One row per user, overwritten
-- when they accept the current balance as correct.
CREATE TABLE personal_balance_baselines (
    user_id BIGINT         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    amount  NUMERIC(14, 2) NOT NULL,
    set_at  TIMESTAMPTZ    NOT NULL
);

-- The account a movement was charged to. Deleting the account keeps the movement:
-- there is simply no balance left to correct when it is later edited or deleted.
ALTER TABLE transactions ADD COLUMN account_id BIGINT REFERENCES accounts (id) ON DELETE SET NULL;
CREATE INDEX idx_transactions_account ON transactions (account_id);
