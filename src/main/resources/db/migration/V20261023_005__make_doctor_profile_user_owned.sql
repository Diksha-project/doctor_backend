ALTER TABLE doctors_details
    ADD COLUMN IF NOT EXISTS user_id BIGINT;

UPDATE doctors_details doctor
SET user_id = app_user.id
FROM app_users app_user
WHERE app_user.doctor_id = doctor.id
  AND doctor.user_id IS NULL;

ALTER TABLE doctors_details ALTER COLUMN user_id SET NOT NULL;

ALTER TABLE doctors_details
    ADD CONSTRAINT fk_doctors_details_user
        FOREIGN KEY (user_id) REFERENCES app_users(id) ON DELETE CASCADE;

ALTER TABLE doctors_details
    ADD CONSTRAINT uk_doctors_details_user UNIQUE (user_id);

ALTER TABLE app_users DROP CONSTRAINT IF EXISTS uk_app_users_doctor;
DROP INDEX IF EXISTS idx_app_users_doctor;
ALTER TABLE app_users DROP COLUMN IF EXISTS doctor_id;

CREATE INDEX IF NOT EXISTS idx_doctors_details_organization_user
    ON doctors_details (organization_id, user_id);
