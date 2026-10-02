package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.entity.ProcessedWhatsappMessage;

public interface ProcessedWhatsappMessageRepo extends JpaRepository<ProcessedWhatsappMessage, String> {

    @Modifying
    @Transactional
    @Query(value = "INSERT INTO processed_whatsapp_messages (message_id) "
            + "VALUES (:messageId) ON CONFLICT (message_id) DO NOTHING", nativeQuery = true)
    int claimMessageId(@Param("messageId") String messageId);
}
