package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.doctor.clinic.DoctorClinic.model.AppointmentOverflowStatus;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "appointment_overflow_requests")
@Getter
@Setter
@NoArgsConstructor
public class AppointmentOverflowRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @Column(name = "target_date", nullable = false)
    private LocalDate targetDate;

    @Column(name = "requested_additional_capacity", nullable = false)
    private Integer requestedAdditionalCapacity;

    @Column(name = "approved_additional_capacity", nullable = false)
    private Integer approvedAdditionalCapacity = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AppointmentOverflowStatus status = AppointmentOverflowStatus.PENDING;

    private String reason;

    @Column(name = "deadline_at", nullable = false)
    private LocalDateTime deadlineAt;

    private LocalDateTime decidedAt;
    private String decidedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); updatedAt = createdAt; }
    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
