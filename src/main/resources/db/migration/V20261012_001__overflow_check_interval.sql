ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS overflow_check_interval_minutes INTEGER NOT NULL DEFAULT 30;
ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS appointment_automation_enabled BOOLEAN NOT NULL DEFAULT TRUE;
