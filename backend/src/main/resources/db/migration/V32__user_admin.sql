-- Administrator flag. No endpoint can set it: it is granted with SQL on the server,
-- so a compromised session can never turn an ordinary account into an admin.
ALTER TABLE users ADD COLUMN admin BOOLEAN NOT NULL DEFAULT FALSE;
