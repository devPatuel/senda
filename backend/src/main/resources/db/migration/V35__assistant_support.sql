-- Starting point of the personal discrepancy: the real total on the day the user
-- turned it on, so older gaps are not dragged along. One row per user, overwritten
-- when they accept the current balance as correct.
CREATE TABLE personal_balance_baselines (
    user_id BIGINT         PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    amount  NUMERIC(14, 2) NOT NULL,
    set_at  TIMESTAMPTZ    NOT NULL
);
