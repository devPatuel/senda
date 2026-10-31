-- Soft delete: the account can no longer be used, but its data stays and it can be
-- restored with SQL. The email remains taken while the row exists.
ALTER TABLE users ADD COLUMN deleted_at TIMESTAMPTZ;
