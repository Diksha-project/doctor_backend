ALTER TABLE organizations
    ADD COLUMN IF NOT EXISTS appointment_automation_enabled BOOLEAN NOT NULL DEFAULT TRUE;
