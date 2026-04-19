package com.doctor.clinic.DoctorClinic.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "appointments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Appointment {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // ========== RELATIONSHIPS ==========
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
    // ========== PATIENT INFORMATION ==========
    
    @Column(name = "patient_name", nullable = false)
    private String patientName;
    
    @Column(name = "patient_phone", nullable = false)
    private String patientPhone;
    
    @Column(name = "patient_email")
    private String patientEmail;
    
    @Column(name = "patient_age")
    private Integer patientAge;
    
    @Column(name = "patient_gender")
    private String patientGender; // MALE, FEMALE, OTHER
    
    // ========== APPOINTMENT DETAILS ==========
    
    @Column(name = "appointment_date", nullable = false)
    private LocalDate appointmentDate;
    
    @Column(name = "appointment_time", nullable = false)
    private LocalTime appointmentTime;
    
    @Column(name = "end_time")
    private LocalTime endTime;
    
    @Column(name = "slot_duration_minutes")
    @Builder.Default
    private Integer slotDurationMinutes = 30;
    
    @Column(name = "appointment_type")
    private String appointmentType; // CONSULTATION, FOLLOW_UP, EMERGENCY, TELEMEDICINE
    
    @Column(name = "reason_for_visit")
    private String reasonForVisit;
    
    @Column(name = "symptoms")
    private String symptoms;
    
    @Column(name = "notes")
    private String notes;
    
    // ========== PAYMENT INFORMATION ==========
    
    @Column(name = "consultation_fee", nullable = false)
    private BigDecimal consultationFee;
    
    @Column(name = "payment_status")
    @Builder.Default
    private String paymentStatus = PaymentStatus.PENDING.name(); // PENDING, PAID, REFUNDED, FAILED
    
    @Column(name = "payment_method")
    private String paymentMethod; // CASH, CARD, UPI, ONLINE
    
    @Column(name = "payment_id")
    private String paymentId; // Transaction ID from payment gateway
    
    @Column(name = "payment_date")
    private LocalDateTime paymentDate;
    
    @Column(name = "discount_amount")
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;
    
    @Column(name = "final_amount")
    private BigDecimal finalAmount;
    
    // ========== APPOINTMENT STATUS ==========
    
    @Column(name = "appointment_status", nullable = false)
    @Builder.Default
    private String appointmentStatus = AppointmentStatus.SCHEDULED.name(); // SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW, RESCHEDULED
    
    @Column(name = "cancellation_reason")
    private String cancellationReason;
    
    @Column(name = "cancelled_by")
    private String cancelledBy; // PATIENT, DOCTOR, SYSTEM
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    // ========== RESCHEDULING ==========
    
    @Column(name = "original_appointment_id")
    private Long originalAppointmentId; // For tracking rescheduled appointments
    
    @Column(name = "rescheduled_count")
    @Builder.Default
    private Integer rescheduledCount = 0;
    
    // ========== REMINDERS ==========
    
    @Column(name = "reminder_sent_24h")
    @Builder.Default
    private boolean reminderSent24h = false;
    
    @Column(name = "reminder_sent_1h")
    @Builder.Default
    private boolean reminderSent1h = false;
    
    @Column(name = "reminder_sent_at")
    private LocalDateTime reminderSentAt;
    
    // ========== FEEDBACK ==========
    
    @Column(name = "patient_feedback")
    private String patientFeedback;
    
    @Column(name = "patient_rating")
    private Integer patientRating; // 1-5
    
    @Column(name = "feedback_received_at")
    private LocalDateTime feedbackReceivedAt;
    
    // ========== AUDIT FIELDS ==========
    
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "created_by")
    private String createdBy; // PATIENT, RECEPTIONIST, DOCTOR, SYSTEM
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        
        // Set end time based on duration
        if (appointmentTime != null && slotDurationMinutes != null) {
            endTime = appointmentTime.plusMinutes(slotDurationMinutes);
        }
        
        // Set final amount
        if (consultationFee != null) {
            finalAmount = consultationFee.subtract(discountAmount != null ? discountAmount : BigDecimal.ZERO);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ========== HELPER METHODS ==========
    
    public boolean isUpcoming() {
        return appointmentDate.isAfter(LocalDate.now()) || 
               (appointmentDate.equals(LocalDate.now()) && appointmentTime.isAfter(LocalTime.now()));
    }
    
    public boolean isOverdue() {
        return appointmentDate.isBefore(LocalDate.now()) ||
               (appointmentDate.equals(LocalDate.now()) && appointmentTime.isBefore(LocalTime.now()));
    }
    
    public boolean isPaid() {
        return PaymentStatus.PAID.name().equals(paymentStatus);
    }
    
    public boolean isCancellable() {
        return AppointmentStatus.SCHEDULED.name().equals(appointmentStatus) || 
               AppointmentStatus.CONFIRMED.name().equals(appointmentStatus);
    }
    
    // ========== ENUMS ==========
    
    public enum AppointmentStatus {
        SCHEDULED,    // Patient booked, waiting for confirmation
        CONFIRMED,    // Doctor confirmed
        COMPLETED,    // Appointment done
        CANCELLED,    // Cancelled
        NO_SHOW,      // Patient didn't show up
        RESCHEDULED   // Rescheduled to another slot
    }
    
    public enum PaymentStatus {
        PENDING,
        PAID,
        REFUNDED,
        FAILED
    }
}
