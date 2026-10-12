ALTER TABLE doctors_details ADD COLUMN IF NOT EXISTS address VARCHAR(1000);
ALTER TABLE doctors_details ADD COLUMN IF NOT EXISTS bio VARCHAR(2000);
CREATE TABLE IF NOT EXISTS doctor_profile_change_requests (
    id BIGSERIAL PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organizations(id),
    doctor_id BIGINT NOT NULL REFERENCES doctors_details(id),
    requester_user_id BIGINT,
    requester_name VARCHAR(255),
    requester_email VARCHAR(255),
    reason VARCHAR(1000) NOT NULL,
    proposed_changes TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    review_note VARCHAR(1000),
    reviewed_by_user_id BIGINT,
    reviewed_by_name VARCHAR(255),
    reviewed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_doctor_profile_requests_org_status
    ON doctor_profile_change_requests (organization_id, status, created_at);
