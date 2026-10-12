ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS overflow_check_interval_minutes INTEGER NOT NULL DEFAULT 30;
