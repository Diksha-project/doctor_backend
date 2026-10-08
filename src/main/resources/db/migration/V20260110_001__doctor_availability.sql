-- Phase 1: Smart Appointment Availability
-- Doctor-configurable weekly schedule, date-specific exceptions, and the
-- settings used by the idempotent slot generator.
-- Idempotent DDL supports applying this migration after baselining a legacy schema.

CREATE TABLE IF NOT EXISTS doctor_availability (
    id BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT NOT NULL REFERENCES doctors_details (id),
    organization_id BIGINT NOT NULL REFERENCES organizations (id),
    day_of_week VARCHAR(16) NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_doctor
    ON doctor_availability (doctor_id, day_of_week);

CREATE TABLE IF NOT EXISTS doctor_availability_settings (
    id BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT NOT NULL UNIQUE REFERENCES doctors_details (id),
    appointment_duration_minutes INTEGER NOT NULL DEFAULT 30,
    max_appointments_per_day INTEGER NOT NULL DEFAULT 20,
    slot_generation_horizon_days INTEGER NOT NULL DEFAULT 30,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE IF NOT EXISTS doctor_availability_exceptions (
    id BIGSERIAL PRIMARY KEY,
    doctor_id BIGINT NOT NULL REFERENCES doctors_details (id),
    organization_id BIGINT NOT NULL REFERENCES organizations (id),
    exception_date DATE NOT NULL,
    exception_type VARCHAR(32) NOT NULL,
    start_time TIME,
    end_time TIME,
    max_appointments INTEGER,
    reason VARCHAR(255),
    created_at TIMESTAMP,
    updated_at TIMESTAMP,
    CONSTRAINT uk_doctor_exception_date UNIQUE (doctor_id, exception_date)
);

CREATE INDEX IF NOT EXISTS idx_doctor_availability_exceptions_doctor_date
    ON doctor_availability_exceptions (doctor_id, exception_date);
