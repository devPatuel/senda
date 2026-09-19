-- Money moved between own accounts (a member funding the couple's shared
-- account, for instance) is not income nor spending: counting it as such
-- inflates the totals and every ratio built on them. Categories marked here
-- are reported apart from income/expense in the monthly summary.
ALTER TABLE categories ADD COLUMN is_transfer BOOLEAN NOT NULL DEFAULT false;
