-- Expense categories can be marked "fixed" (rent, insurance...) so the
-- monthly summary can split fixed vs. variable spend. Defaults to false
-- (variable) for existing rows and for INCOME categories, where it is unused.
ALTER TABLE categories ADD COLUMN fixed BOOLEAN NOT NULL DEFAULT false;
