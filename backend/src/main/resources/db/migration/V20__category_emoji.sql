-- Phase 20 (F6): categories can carry an optional identifying emoji.
-- NULL means "no emoji". VARCHAR(16) leaves headroom over the app-level
-- @Size(max = 8) limit, because Postgres counts code points while the Java
-- validation counts UTF-16 code units.
ALTER TABLE categories ADD COLUMN emoji VARCHAR(16);
