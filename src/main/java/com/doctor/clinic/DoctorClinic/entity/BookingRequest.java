package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDateTime;

import com.doctor.clinic.DoctorClinic.model.BookingRequestStatus;

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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Captured when a patient wants an appointment but the doctor has no open
 * slots, so the clinic can follow up instead of the patient being dropped.
 */
@Entity
@Table(name = "booking_requests")
@Getter
@Setter
@NoArgsConstructor
public class BookingRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "patient_id")
    private Patient patient;

    @Column(name = "patient_name")
    private String patientName;

    @Column(name = "patient_phone")
    private String patientPhone;

    @Column(name = "requested_date")
    private java.time.LocalDate requestedDate;

    @Column(name = "requested_time")
    private java.time.LocalTime requestedTime;

    /** Why the booking couldn't be completed, e.g. "No slots available", "Daily limit reached". */
    @Column(name = "reason", length = 100)
    private String reason;

    @Column(name = "appointment_type")
    private String appointmentType;

    @Column(name = "offered_slot_ids", length = 200)
    private String offeredSlotIds;

    @Column(name = "offered_at")
    private LocalDateTime offeredAt;

    @Column(nullable = false, length = 32)
    private String source = "WHATSAPP";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private BookingRequestStatus status = BookingRequestStatus.PENDING;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
