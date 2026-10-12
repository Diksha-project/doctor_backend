package com.doctor.clinic.DoctorClinic.entity;

import java.time.LocalDateTime;
import com.doctor.clinic.DoctorClinic.model.DoctorProfileChangeStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "doctor_profile_change_requests")
@Getter @Setter @NoArgsConstructor
public class DoctorProfileChangeRequest {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "organization_id", nullable = false) private Organization organization;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "doctor_id", nullable = false) private Doctor doctor;
    private Long requesterUserId;
    private String requesterName;
    private String requesterEmail;
    @Column(nullable = false, length = 1000) private String reason;
    @Column(nullable = false, columnDefinition = "TEXT") private String proposedChanges;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DoctorProfileChangeStatus status = DoctorProfileChangeStatus.PENDING;
    @Column(length = 1000) private String reviewNote;
    private Long reviewedByUserId;
    private String reviewedByName;
    private LocalDateTime reviewedAt;
    @Column(nullable = false, updatable = false) private LocalDateTime createdAt;
    @PrePersist void onCreate() { createdAt = LocalDateTime.now(); }
}
