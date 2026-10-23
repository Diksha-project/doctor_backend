INSERT INTO app_roles (organization_id, code, name, description, system_role, active)
VALUES (NULL, 'PLATFORM_ADMIN', 'Platform Admin',
        'Internal Archster platform administration only', TRUE, TRUE)
ON CONFLICT (code) WHERE organization_id IS NULL DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_role = TRUE,
    active = TRUE;

DELETE FROM app_role_permissions
WHERE role_id = (
    SELECT id FROM app_roles
    WHERE organization_id IS NULL AND code = 'PLATFORM_ADMIN'
);
