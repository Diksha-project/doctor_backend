DO $$
DECLARE
    existing_constraint RECORD;
BEGIN
    FOR existing_constraint IN
        SELECT conname
        FROM pg_constraint
        WHERE conrelid = 'organizations'::regclass
          AND contype = 'c'
          AND pg_get_constraintdef(oid) ILIKE '%organization_type%'
    LOOP
        EXECUTE format('ALTER TABLE organizations DROP CONSTRAINT %I', existing_constraint.conname);
    END LOOP;
END $$;

ALTER TABLE organizations
    ALTER COLUMN organization_type TYPE VARCHAR(40)
    USING organization_type::text;

ALTER TABLE organizations
    ADD CONSTRAINT organizations_organization_type_check
    CHECK (organization_type IN ('CLINIC', 'NURSING_HOME', 'HOSPITAL'));
