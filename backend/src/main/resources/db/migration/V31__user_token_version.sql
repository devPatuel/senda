-- Sessions are stateless JWTs, so the only way to close the ones already issued
-- is to make them stop matching: each token carries the version it was issued
-- with, and changing the password bumps it.
ALTER TABLE users ADD COLUMN token_version INT NOT NULL DEFAULT 0;
