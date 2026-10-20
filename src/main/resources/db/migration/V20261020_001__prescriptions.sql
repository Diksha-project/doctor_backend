CREATE TABLE prescriptions (
    id UUID PRIMARY KEY,
    version BIGINT NOT NULL DEFAULT 0,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    doctor_id BIGINT NOT NULL REFERENCES doctors_details(id),
    patient_id BIGINT NOT NULL REFERENCES patients(id),
    appointment_id BIGINT REFERENCES appointments(id),
    replaces_id UUID REFERENCES prescriptions(id),
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    content TEXT NOT NULL,
    identity_snapshot TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    finalized_at TIMESTAMP WITH TIME ZONE,
    finalized_by BIGINT
);
CREATE INDEX idx_prescriptions_scope ON prescriptions(organization_id, doctor_id, created_at DESC);
CREATE INDEX idx_prescriptions_patient ON prescriptions(organization_id, doctor_id, patient_id);
INSERT INTO app_permissions (code, module, action, description) VALUES
('PRESCRIPTIONS:VIEW', 'PRESCRIPTIONS', 'VIEW', 'View prescriptions'),
('PRESCRIPTIONS:EDIT', 'PRESCRIPTIONS', 'EDIT', 'Create and edit prescription drafts'),
('PRESCRIPTIONS:FINALIZE', 'PRESCRIPTIONS', 'FINALIZE', 'Finalize own prescriptions')
ON CONFLICT (code) DO NOTHING;
