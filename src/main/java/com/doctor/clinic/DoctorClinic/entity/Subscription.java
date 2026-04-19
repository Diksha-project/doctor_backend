package com.doctor.clinic.DoctorClinic.entity;


import lombok.Data;
import lombok.Builder;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String subscriptionId; // From payment gateway
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "organization_id", nullable = false)
    private Organization organization;
    
    private String planType; // BASIC, PROFESSIONAL, ENTERPRISE
    
    private String billingCycle; // MONTHLY, YEARLY
    
    private LocalDate startDate;
    
    private LocalDate endDate;
    
    private LocalDate trialEndDate;
    
    private String status = "ACTIVE"; // ACTIVE, EXPIRED, CANCELLED, TRIAL
    
    private BigDecimal amount;
    
    private Integer maxDoctors; // Override organization's max doctors if needed
    
    private Boolean autoRenew = true;
    
    @Column(updatable = false)
    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public boolean isActive() {
        return "ACTIVE".equals(status) && 
               (endDate == null || !LocalDate.now().isAfter(endDate));
    }
    
    public boolean isInTrial() {
        return "TRIAL".equals(status) && 
               trialEndDate != null && 
               !LocalDate.now().isAfter(trialEndDate);
    }
}
