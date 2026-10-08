package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-doctor settings governing how availability translates into bookable
 * slots: appointment duration and the maximum number of appointments the
 * doctor accepts in a single day.
 */
@Entity
@Table(name = "doctor_availability_settings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAvailabilitySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false, unique = true)
    private Doctor doctor;

    @Column(name = "appointment_duration_minutes", nullable = false)
    @Builder.Default
    private Integer appointmentDurationMinutes = 30;

    @Column(name = "max_appointments_per_day", nullable = false)
    @Builder.Default
    private Integer maxAppointmentsPerDay = 20;

    @Column(name = "slot_generation_horizon_days", nullable = false)
    @Builder.Default
    private Integer slotGenerationHorizonDays = 30;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
