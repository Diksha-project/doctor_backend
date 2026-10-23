ALTER TABLE demo_requests
    ALTER COLUMN email DROP NOT NULL;

ALTER TABLE demo_requests
    DROP COLUMN IF EXISTS organization_type;
