UPDATE doctors_details
SET doctor_email = 'john@clinic.com',
    updated_at = CURRENT_TIMESTAMP
WHERE first_name = 'John'
  AND last_name = 'Smith';

INSERT INTO app_users (organization_id, doctor_id, full_name, email, password_hash, active, created_at, updated_at)
SELECT d.organization_id,
       d.id,
       trim(concat(d.first_name, ' ', coalesce(d.last_name, ''))),
       lower(d.doctor_email),
       o.password_hash,
       d.status IS NULL OR d.status = 'ACTIVE',
       coalesce(d.created_at, CURRENT_TIMESTAMP),
       CURRENT_TIMESTAMP
FROM doctors_details d
JOIN organizations o ON o.id = d.organization_id
WHERE d.doctor_email = 'john@clinic.com'
ON CONFLICT (email) DO UPDATE SET
    organization_id = EXCLUDED.organization_id,
    doctor_id = EXCLUDED.doctor_id,
    full_name = EXCLUDED.full_name,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO app_user_roles (user_id, role_id, scope)
SELECT u.id, r.id, 'OWN_DOCTOR'
FROM app_users u
JOIN app_roles r ON r.organization_id IS NULL AND r.code = 'DOCTOR'
WHERE u.email = 'john@clinic.com'
ON CONFLICT DO NOTHING;
