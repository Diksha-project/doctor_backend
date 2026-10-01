package com.doctor.clinic.DoctorClinic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "processed_whatsapp_messages")
public class ProcessedWhatsappMessage {

    @Id
    @Column(name = "message_id", nullable = false, updatable = false)
    private String messageId;

    protected ProcessedWhatsappMessage() {
    }

    public ProcessedWhatsappMessage(String messageId) {
        this.messageId = messageId;
    }
}
