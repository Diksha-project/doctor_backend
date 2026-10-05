package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDateTime;

import com.doctor.clinic.DoctorClinic.model.ResponseMode;
import com.doctor.clinic.DoctorClinic.model.ResponseRuleTriggerType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "whatsapp_response_rules",
        indexes = {
                @Index(name = "idx_whatsapp_rule_org_priority", columnList = "organization_id, priority"),
                @Index(name = "idx_whatsapp_rule_doctor_active", columnList = "doctor_id, active")
        })
@Getter
@Setter
@NoArgsConstructor
public class WhatsAppResponseRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "trigger_type", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private ResponseRuleTriggerType triggerType;

    @Column(name = "trigger_value", nullable = false, length = 200)
    private String triggerValue;

    @Column(name = "response_mode", nullable = false, length = 32)
    @Enumerated(EnumType.STRING)
    private ResponseMode responseMode = ResponseMode.TEXT;

    @Column(name = "response_text", nullable = false, columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;

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
