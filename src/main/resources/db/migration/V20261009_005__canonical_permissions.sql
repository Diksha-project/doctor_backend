-- Add the canonical permission catalogue without removing legacy rows or links.
-- Keeping both makes this migration forward-only, idempotent, and recoverable.
INSERT INTO app_permissions (code, module, action, description) VALUES
('DASHBOARD:VIEW', 'DASHBOARD', 'VIEW', 'View dashboard'),
('PATIENTS:VIEW', 'PATIENTS', 'VIEW', 'View patients'),
('PATIENTS:CREATE', 'PATIENTS', 'CREATE', 'Create patients'),
('PATIENTS:EDIT', 'PATIENTS', 'EDIT', 'Edit patients'),
('PATIENTS:DELETE', 'PATIENTS', 'DELETE', 'Delete patients'),
('APPOINTMENTS:VIEW', 'APPOINTMENTS', 'VIEW', 'View appointments'),
('APPOINTMENTS:CREATE', 'APPOINTMENTS', 'CREATE', 'Create appointments'),
('APPOINTMENTS:EDIT', 'APPOINTMENTS', 'EDIT', 'Edit appointments'),
('APPOINTMENTS:CANCEL', 'APPOINTMENTS', 'CANCEL', 'Cancel appointments'),
('AVAILABILITY:VIEW', 'AVAILABILITY', 'VIEW', 'View availability'),
('AVAILABILITY:EDIT', 'AVAILABILITY', 'EDIT', 'Edit availability'),
('WHATSAPP_INBOX:VIEW', 'WHATSAPP_INBOX', 'VIEW', 'View WhatsApp inbox'),
('WHATSAPP_INBOX:REPLY', 'WHATSAPP_INBOX', 'REPLY', 'Reply to WhatsApp messages'),
('WHATSAPP_INBOX:TAKEOVER', 'WHATSAPP_INBOX', 'TAKEOVER', 'Take over conversations'),
('WHATSAPP_INBOX:CLOSE', 'WHATSAPP_INBOX', 'CLOSE', 'Close conversations'),
('BOOKING_REQUESTS:VIEW', 'BOOKING_REQUESTS', 'VIEW', 'View booking requests'),
('BOOKING_REQUESTS:EDIT', 'BOOKING_REQUESTS', 'EDIT', 'Edit booking requests'),
('DOCTORS:VIEW', 'DOCTORS', 'VIEW', 'View doctors'),
('DOCTORS:EDIT', 'DOCTORS', 'EDIT', 'Edit doctors'),
('USERS:VIEW', 'USERS', 'VIEW', 'View users'),
('USERS:EDIT', 'USERS', 'EDIT', 'Edit users'),
('ROLES:VIEW', 'ROLES', 'VIEW', 'View roles'),
('ROLES:EDIT', 'ROLES', 'EDIT', 'Edit roles'),
('AI_ASSISTANT:VIEW', 'AI_ASSISTANT', 'VIEW', 'View AI assistant'),
('AI_ASSISTANT:CONFIGURE', 'AI_ASSISTANT', 'CONFIGURE', 'Configure AI assistant'),
('WHATSAPP_CONFIGURATION:VIEW', 'WHATSAPP_CONFIGURATION', 'VIEW', 'View WhatsApp configuration'),
('WHATSAPP_CONFIGURATION:EDIT', 'WHATSAPP_CONFIGURATION', 'EDIT', 'Edit WhatsApp configuration'),
('REPORTS:VIEW', 'REPORTS', 'VIEW', 'View reports'),
('ORGANIZATION:VIEW', 'ORGANIZATION', 'VIEW', 'View organization'),
('ORGANIZATION:EDIT', 'ORGANIZATION', 'EDIT', 'Edit organization'),
('SETTINGS:VIEW', 'SETTINGS', 'VIEW', 'View settings'),
('SETTINGS:EDIT', 'SETTINGS', 'EDIT', 'Edit settings')
ON CONFLICT (code) DO UPDATE SET
    module = EXCLUDED.module,
    action = EXCLUDED.action,
    description = EXCLUDED.description;

WITH permission_mapping(legacy_code, canonical_code) AS (VALUES
    ('DASHBOARD_VIEW', 'DASHBOARD:VIEW'),
    ('PATIENTS_VIEW', 'PATIENTS:VIEW'),
    ('PATIENTS_CREATE', 'PATIENTS:CREATE'),
    ('PATIENTS_EDIT', 'PATIENTS:EDIT'),
    ('PATIENTS_DELETE', 'PATIENTS:DELETE'),
    ('APPOINTMENTS_VIEW', 'APPOINTMENTS:VIEW'),
    ('APPOINTMENTS_CREATE', 'APPOINTMENTS:CREATE'),
    ('APPOINTMENTS_EDIT', 'APPOINTMENTS:EDIT'),
    ('APPOINTMENTS_CANCEL', 'APPOINTMENTS:CANCEL'),
    ('AVAILABILITY_VIEW', 'AVAILABILITY:VIEW'),
    ('AVAILABILITY_MANAGE', 'AVAILABILITY:EDIT'),
    ('MESSAGES_VIEW', 'WHATSAPP_INBOX:VIEW'),
    ('MESSAGES_SEND', 'WHATSAPP_INBOX:REPLY'),
    ('MESSAGES_TAKEOVER', 'WHATSAPP_INBOX:TAKEOVER'),
    ('MESSAGES_MANAGE', 'WHATSAPP_INBOX:CLOSE'),
    ('BOOKING_REQUESTS_VIEW', 'BOOKING_REQUESTS:VIEW'),
    ('BOOKING_REQUESTS_MANAGE', 'BOOKING_REQUESTS:EDIT'),
    ('DOCTORS_VIEW', 'DOCTORS:VIEW'),
    ('DOCTORS_MANAGE', 'DOCTORS:EDIT'),
    ('USERS_VIEW', 'USERS:VIEW'),
    ('USERS_MANAGE', 'USERS:EDIT'),
    ('ROLES_VIEW', 'ROLES:VIEW'),
    ('ROLES_MANAGE', 'ROLES:EDIT'),
    ('AI_VIEW', 'AI_ASSISTANT:VIEW'),
    ('AI_MANAGE', 'AI_ASSISTANT:CONFIGURE'),
    ('WHATSAPP_VIEW', 'WHATSAPP_CONFIGURATION:VIEW'),
    ('WHATSAPP_MANAGE', 'WHATSAPP_CONFIGURATION:EDIT'),
    ('REPORTS_VIEW', 'REPORTS:VIEW'),
    ('ORGANIZATION_VIEW', 'ORGANIZATION:VIEW'),
    ('ORGANIZATION_MANAGE', 'ORGANIZATION:EDIT'),
    ('SETTINGS_VIEW', 'SETTINGS:VIEW'),
    ('SETTINGS_MANAGE', 'SETTINGS:EDIT')
)
INSERT INTO app_role_permissions (role_id, permission_id)
SELECT DISTINCT rp.role_id, canonical.id
FROM app_role_permissions rp
JOIN app_permissions legacy ON legacy.id = rp.permission_id
JOIN permission_mapping mapping ON mapping.legacy_code = legacy.code
JOIN app_permissions canonical ON canonical.code = mapping.canonical_code
ON CONFLICT (role_id, permission_id) DO NOTHING;
