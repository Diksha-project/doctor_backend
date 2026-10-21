ALTER TABLE organizations ADD COLUMN IF NOT EXISTS clinic_address VARCHAR(1000);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS clinic_phone VARCHAR(40);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS clinic_email VARCHAR(254);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS clinic_website VARCHAR(500);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS clinic_tagline VARCHAR(200);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS logo_object_key VARCHAR(1000);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS logo_content_type VARCHAR(100);
ALTER TABLE organizations ADD COLUMN IF NOT EXISTS logo_file_name VARCHAR(255);

CREATE TABLE prescription_document_templates (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    doctor_id BIGINT REFERENCES doctors_details(id),
    name VARCHAR(120) NOT NULL,
    template_type VARCHAR(40) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    is_default BOOLEAN NOT NULL DEFAULT FALSE,
    configuration TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_prescription_templates_scope ON prescription_document_templates(organization_id, doctor_id, status);
CREATE INDEX idx_prescription_template_name ON prescription_document_templates(organization_id, doctor_id, name);

ALTER TABLE prescriptions ADD COLUMN IF NOT EXISTS template_snapshot TEXT;
