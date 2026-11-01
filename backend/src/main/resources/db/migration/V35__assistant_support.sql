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

-- What a token may do. Existing tokens become QUICK, which is exactly what they can do today.
ALTER TABLE api_tokens ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'QUICK';

-- Name of the token that typed the movement; null means the web. A name and not a
-- foreign key, so the author is still known after the token is revoked or removed.
ALTER TABLE transactions ADD COLUMN created_via VARCHAR(100);
CREATE INDEX idx_transactions_created_via ON transactions (created_via) WHERE created_via IS NOT NULL;
