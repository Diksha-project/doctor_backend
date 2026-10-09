UPDATE app_users u
SET full_name = o.owner_full_name,
    doctor_id = NULL,
    active = COALESCE(o.is_active, TRUE),
    updated_at = CURRENT_TIMESTAMP
FROM organizations o
WHERE lower(u.email) = lower(o.owner_email);

INSERT INTO app_user_roles (user_id, role_id, scope)
SELECT u.id, r.id, 'ORGANIZATION'
FROM app_users u
JOIN organizations o ON o.id = u.organization_id AND lower(o.owner_email) = lower(u.email)
JOIN app_roles r ON r.organization_id IS NULL AND r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
