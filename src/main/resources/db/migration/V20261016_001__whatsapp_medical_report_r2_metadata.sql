ALTER TABLE whatsapp_messages
    ADD COLUMN IF NOT EXISTS attachment_object_key VARCHAR(1024),
    ADD COLUMN IF NOT EXISTS attachment_mime_type VARCHAR(128),
    ADD COLUMN IF NOT EXISTS attachment_file_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attachment_size BIGINT,
    ADD COLUMN IF NOT EXISTS medical_report BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE patient_chat_messages
    ADD COLUMN IF NOT EXISTS attachment_object_key VARCHAR(1024);
