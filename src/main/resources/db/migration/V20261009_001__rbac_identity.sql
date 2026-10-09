CREATE TABLE IF NOT EXISTS app_users (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    doctor_id BIGINT REFERENCES doctors_details(id),
    full_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_login_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_users_email UNIQUE (email),
    CONSTRAINT uk_app_users_doctor UNIQUE (doctor_id)
);

CREATE INDEX IF NOT EXISTS idx_app_users_org ON app_users (organization_id);
CREATE INDEX IF NOT EXISTS idx_app_users_doctor ON app_users (doctor_id);

CREATE TABLE IF NOT EXISTS app_permissions (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    module VARCHAR(80) NOT NULL,
    action VARCHAR(80) NOT NULL,
    description VARCHAR(255)
);

CREATE TABLE IF NOT EXISTS app_roles (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT REFERENCES organizations(id),
    code VARCHAR(80) NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(255),
    system_role BOOLEAN NOT NULL DEFAULT FALSE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_app_roles_org_code UNIQUE (organization_id, code)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_app_roles_global_code
    ON app_roles (code)
    WHERE organization_id IS NULL;

CREATE TABLE IF NOT EXISTS app_role_permissions (
    role_id BIGINT NOT NULL REFERENCES app_roles(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES app_permissions(id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS app_user_roles (
    user_id BIGINT NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES app_roles(id),
    scope VARCHAR(32) NOT NULL DEFAULT 'ORGANIZATION',
    PRIMARY KEY (user_id, role_id)
);

INSERT INTO app_permissions (code, module, action, description) VALUES
('DASHBOARD_VIEW', 'Dashboard', 'VIEW', 'View dashboard'),
('PATIENTS_VIEW', 'Patients', 'VIEW', 'View patients'),
('PATIENTS_CREATE', 'Patients', 'CREATE', 'Create patients'),
('PATIENTS_EDIT', 'Patients', 'EDIT', 'Edit patients'),
('PATIENTS_DELETE', 'Patients', 'DELETE', 'Delete patients'),
('APPOINTMENTS_VIEW', 'Appointments', 'VIEW', 'View appointments'),
('APPOINTMENTS_CREATE', 'Appointments', 'CREATE', 'Create appointments'),
('APPOINTMENTS_EDIT', 'Appointments', 'EDIT', 'Edit appointments'),
('APPOINTMENTS_CANCEL', 'Appointments', 'CANCEL', 'Cancel appointments'),
('AVAILABILITY_VIEW', 'Availability', 'VIEW', 'View availability'),
('AVAILABILITY_MANAGE', 'Availability', 'MANAGE', 'Manage availability'),
('MESSAGES_VIEW', 'WhatsApp Messages', 'VIEW', 'View messages'),
('MESSAGES_SEND', 'WhatsApp Messages', 'SEND', 'Send messages'),
('MESSAGES_TAKEOVER', 'WhatsApp Messages', 'TAKEOVER', 'Take over conversations'),
('MESSAGES_MANAGE', 'WhatsApp Messages', 'MANAGE', 'Manage conversations'),
('BOOKING_REQUESTS_VIEW', 'Booking Requests', 'VIEW', 'View booking requests'),
('BOOKING_REQUESTS_MANAGE', 'Booking Requests', 'MANAGE', 'Manage booking requests'),
('DOCTORS_VIEW', 'Doctors', 'VIEW', 'View doctors'),
('DOCTORS_MANAGE', 'Doctors', 'MANAGE', 'Manage doctors'),
('USERS_VIEW', 'Users', 'VIEW', 'View users'),
('USERS_MANAGE', 'Users', 'MANAGE', 'Manage users'),
('ROLES_VIEW', 'Roles', 'VIEW', 'View roles'),
('ROLES_MANAGE', 'Roles', 'MANAGE', 'Manage roles'),
('AI_VIEW', 'AI Assistant', 'VIEW', 'View AI assistant'),
('AI_MANAGE', 'AI Assistant', 'MANAGE', 'Manage AI assistant'),
('WHATSAPP_VIEW', 'WhatsApp Configuration', 'VIEW', 'View WhatsApp configuration'),
('WHATSAPP_MANAGE', 'WhatsApp Configuration', 'MANAGE', 'Manage WhatsApp configuration'),
('REPORTS_VIEW', 'Reports', 'VIEW', 'View reports'),
('ORGANIZATION_VIEW', 'Organization', 'VIEW', 'View organization'),
('ORGANIZATION_MANAGE', 'Organization', 'MANAGE', 'Manage organization'),
('SETTINGS_VIEW', 'Settings', 'VIEW', 'View settings'),
('SETTINGS_MANAGE', 'Settings', 'MANAGE', 'Manage settings')
ON CONFLICT (code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

INSERT INTO app_roles (organization_id, code, name, description, system_role, active) VALUES
(NULL, 'SUPER_ADMIN', 'Super Admin', 'Full organization administration', TRUE, TRUE),
(NULL, 'DOCTOR', 'Doctor', 'Default doctor workflow access', TRUE, TRUE)
ON CONFLICT (code) WHERE organization_id IS NULL DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    system_role = TRUE,
    active = TRUE;

INSERT INTO app_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM app_roles r
CROSS JOIN app_permissions p
WHERE r.organization_id IS NULL AND r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;

INSERT INTO app_role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM app_roles r
JOIN app_permissions p ON p.code IN (
    'DASHBOARD_VIEW',
    'PATIENTS_VIEW',
    'APPOINTMENTS_VIEW',
    'APPOINTMENTS_CREATE',
    'APPOINTMENTS_EDIT',
    'APPOINTMENTS_CANCEL',
    'AVAILABILITY_VIEW',
    'AVAILABILITY_MANAGE',
    'MESSAGES_VIEW',
    'MESSAGES_SEND',
    'MESSAGES_TAKEOVER',
    'BOOKING_REQUESTS_VIEW',
    'BOOKING_REQUESTS_MANAGE',
    'AI_VIEW',
    'REPORTS_VIEW',
    'SETTINGS_VIEW'
)
WHERE r.organization_id IS NULL AND r.code = 'DOCTOR'
ON CONFLICT DO NOTHING;

INSERT INTO app_users (organization_id, full_name, email, password_hash, active, last_login_at, created_at, updated_at)
SELECT o.id,
       o.owner_full_name,
       o.owner_email,
       o.password_hash,
       COALESCE(o.is_active, TRUE),
       o.last_login_at,
       COALESCE(o.created_at, CURRENT_TIMESTAMP),
       COALESCE(o.updated_at, CURRENT_TIMESTAMP)
FROM organizations o
WHERE o.owner_email IS NOT NULL
ON CONFLICT (email) DO UPDATE SET
    organization_id = EXCLUDED.organization_id,
    full_name = EXCLUDED.full_name,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO app_user_roles (user_id, role_id, scope)
SELECT u.id, r.id, 'ORGANIZATION'
FROM app_users u
JOIN organizations o ON o.id = u.organization_id AND o.owner_email = u.email
JOIN app_roles r ON r.organization_id IS NULL AND r.code = 'SUPER_ADMIN'
ON CONFLICT DO NOTHING;
