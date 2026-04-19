package com.doctor.clinic.DoctorClinic.entity;




import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "doctor_slots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorSlot {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false)
    private Doctor doctor;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
    @Column(name = "slot_date", nullable = false)
    private LocalDate slotDate;
    
    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;
    
    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;
    
    @Column(name = "duration_minutes")
    @Builder.Default
    private Integer durationMinutes = 30;
    
    @Column(name = "is_available")
    @Builder.Default
    private boolean isAvailable = true;
    
    @Column(name = "max_appointments")
    @Builder.Default
    private Integer maxAppointments = 1;
    
    @Column(name = "booked_count")
    @Builder.Default
    private Integer bookedCount = 0;
    
    @Column(name = "slot_type")
    private String slotType; // REGULAR, BREAK, EMERGENCY, TELEMEDICINE
    
    @Column(name = "notes")
    private String notes;
    
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
    
    public boolean hasAvailability() {
        return isAvailable && bookedCount < maxAppointments;
    }
}
