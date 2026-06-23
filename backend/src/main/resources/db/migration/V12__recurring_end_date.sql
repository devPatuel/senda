-- Phase 12: optional cancellation deadline ("fecha límite de baja") per
-- recurring payment. Informational only: it never changes the next due date or
-- the monthly-equivalent cost; the UI surfaces it as a reminder.

ALTER TABLE recurring_payments ADD COLUMN end_date DATE;
