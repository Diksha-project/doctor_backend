package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patient_booking_sessions", uniqueConstraints = @UniqueConstraint(name = "uk_booking_patient_doctor", columnNames = {"patient_id", "doctor_id"}))
@Getter @Setter @NoArgsConstructor
public class PatientBookingSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "patient_id", nullable = false) private Patient patient;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    @Column(nullable = false, length = 32) private String step;
    @Column(name = "appointment_type") private String appointmentType;
    @Column(name = "appointment_date") private LocalDate appointmentDate;
    @Column(name = "appointment_time") private LocalTime appointmentTime;
    @Column(name = "updated_at", nullable = false) private LocalDateTime updatedAt = LocalDateTime.now();
    @PreUpdate @PrePersist void touch() { updatedAt = LocalDateTime.now(); }
}
