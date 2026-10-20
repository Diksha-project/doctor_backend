package com.doctor.clinic.DoctorClinic.prescription;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "prescriptions")
@Getter @Setter
public class Prescription {
    @Id private UUID id;
    @Version private long version;
    @Column(nullable = false) private Long organizationId;
    @Column(nullable = false) private Long doctorId;
    @Column(nullable = false) private Long patientId;
    private Long appointmentId;
    private UUID replacesId;
    @Column(nullable = false) private String status = "DRAFT";
    @Column(nullable = false, columnDefinition = "TEXT") private String content;
    @Column(columnDefinition = "TEXT") private String identitySnapshot;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();
    private Instant finalizedAt;
    private Long finalizedBy;
}
