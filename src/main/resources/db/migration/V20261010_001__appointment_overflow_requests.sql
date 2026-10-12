CREATE TABLE IF NOT EXISTS appointment_overflow_requests (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    doctor_id BIGINT NOT NULL REFERENCES doctors_details(id),
    target_date DATE NOT NULL,
    requested_additional_capacity INTEGER NOT NULL,
    approved_additional_capacity INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reason VARCHAR(1000),
    deadline_at TIMESTAMP NOT NULL,
    decided_at TIMESTAMP,
    decided_by VARCHAR(255),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_overflow_requests_org_status
    ON appointment_overflow_requests (organization_id, status);
CREATE INDEX IF NOT EXISTS idx_overflow_requests_deadline
    ON appointment_overflow_requests (status, deadline_at);
