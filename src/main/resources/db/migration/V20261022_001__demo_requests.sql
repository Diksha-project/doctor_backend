CREATE TABLE IF NOT EXISTS demo_requests (
    id BIGSERIAL PRIMARY KEY,
    organization_name VARCHAR(150) NOT NULL,
    contact_name VARCHAR(120) NOT NULL,
    email VARCHAR(254) NOT NULL,
    phone VARCHAR(30) NOT NULL,
    organization_type VARCHAR(40) NOT NULL,
    city VARCHAR(120),
    expected_doctors INTEGER,
    message VARCHAR(1000),
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    internal_notes VARCHAR(2000),
    requested_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_demo_requests_status_requested
    ON demo_requests(status, requested_at DESC);
