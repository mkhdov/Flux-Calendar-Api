-- Add missing sent_at column that the Reminder entity requires
-- This migration is needed for existing deployments where V1 already ran without this column.
ALTER TABLE reminders ADD COLUMN IF NOT EXISTS sent_at TIMESTAMPTZ;
