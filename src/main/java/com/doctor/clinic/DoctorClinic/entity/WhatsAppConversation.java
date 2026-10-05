package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDateTime;

import com.doctor.clinic.DoctorClinic.model.AutomationMode;
import com.doctor.clinic.DoctorClinic.model.ConversationStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "whatsapp_conversations",
        uniqueConstraints = @UniqueConstraint(name = "uk_whatsapp_conversation_org_patient_phone",
                columnNames = {"organization_id", "patient_id", "phone_number"}))
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @Column(name = "phone_number", nullable = false, length = 32)
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConversationStatus status = ConversationStatus.AI_ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "automation_mode", nullable = false, length = 16)
    private AutomationMode automationMode = AutomationMode.HYBRID;

    @Column(name = "ai_enabled", nullable = false)
    private boolean aiEnabled = true;

    @Column(name = "human_takeover", nullable = false)
    private boolean humanTakeover = false;

    @Column(name = "last_message_at")
    private LocalDateTime lastMessageAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PrePersist
    @PreUpdate
    protected void touch() {
        updatedAt = LocalDateTime.now();
    }
}
