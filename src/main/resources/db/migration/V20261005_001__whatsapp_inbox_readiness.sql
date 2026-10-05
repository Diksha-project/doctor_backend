CREATE UNIQUE INDEX IF NOT EXISTS uk_processed_whatsapp_message_id
    ON processed_whatsapp_messages (message_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_whatsapp_meta_message_id_not_null
    ON whatsapp_messages (meta_message_id)
    WHERE meta_message_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_whatsapp_conversation_org_last_message
    ON whatsapp_conversations (organization_id, last_message_at DESC);

CREATE INDEX IF NOT EXISTS idx_whatsapp_conversation_org_status
    ON whatsapp_conversations (organization_id, status);

CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_org_conversation_created
    ON whatsapp_messages (organization_id, conversation_id, created_at);

CREATE INDEX IF NOT EXISTS idx_whatsapp_messages_patient_created
    ON whatsapp_messages (patient_id, created_at);

CREATE INDEX IF NOT EXISTS idx_whatsapp_rule_org_priority_active
    ON whatsapp_response_rules (organization_id, active, priority DESC);
