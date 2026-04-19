package com.doctor.clinic.DoctorClinic.entity;


import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "doctors_details")
@NoArgsConstructor
@Data
@Builder
@AllArgsConstructor
public class Doctor {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false)
    private String firstName;
    
    private String lastName;
    
    @Column(unique = true, nullable = false)
    private String registrationNumber;
    
    private String qualification;
    
    private String specialization;
    
    private Integer experienceYears;
    
   
	@NotBlank(message = "Mobile number is required")
	@Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
	@Column(name = "doctor_mobile", nullable = false, unique = true)
    private String phoneNumber; // WhatsApp number
    

	@Email(message = "Invalid email format")
	@Column(name = "doctor_email", nullable = false, unique = true)
    private String email;
    
    private BigDecimal consultationFee;
    
    private String consultationHours; // Simple text format
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
 
    
    private String status = "ACTIVE"; // ACTIVE, INACTIVE

    @Column(name = "default_slot_duration_minutes")
    @Builder.Default
    private Integer defaultSlotDurationMinutes = 5;
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public String getFullName() {
        return lastName != null ? firstName + " " + lastName : firstName;
    }

	
}