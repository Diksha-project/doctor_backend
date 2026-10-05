package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedWhatsappMessageRepoImpl implements ProcessedWhatsappMessageCustomRepo {

    private final JdbcTemplate jdbcTemplate;

    public ProcessedWhatsappMessageRepoImpl(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public int claimMessageId(String messageId) {
        try {
            return jdbcTemplate.update(
                    "INSERT INTO processed_whatsapp_messages (message_id) VALUES (?)",
                    messageId
            );
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }
}
