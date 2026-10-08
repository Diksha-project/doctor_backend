CREATE TABLE IF NOT EXISTS booking_requests (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    doctor_id BIGINT NOT NULL REFERENCES doctors_details(id),
    patient_id BIGINT REFERENCES patients(id),
    patient_name VARCHAR(255),
    patient_phone VARCHAR(255),
    appointment_type VARCHAR(255),
    source VARCHAR(32) NOT NULL DEFAULT 'WHATSAPP',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    note VARCHAR(500),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_booking_requests_org_status ON booking_requests (organization_id, status);
