package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Index;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_chat_messages", indexes = {
        @Index(name = "idx_patient_chat_timeline", columnList = "patient_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
public class PatientChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "whatsapp_message_id", unique = true)
    private String whatsappMessageId;

    @Column(name = "reply_to_message_id")
    private String replyToMessageId;

    @Column(name = "message_type", nullable = false, length = 32)
    private String messageType;

    @Column(name = "message_text", columnDefinition = "TEXT")
    private String messageText;

    @Column(name = "attachment_mime_type", length = 128)
    private String attachmentMimeType;

    @Column(name = "attachment_name")
    private String attachmentName;

    @Column(name = "attachment_data", columnDefinition = "bytea")
    private byte[] attachmentData;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
}
