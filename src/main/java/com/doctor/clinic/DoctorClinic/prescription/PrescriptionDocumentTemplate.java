package com.doctor.clinic.DoctorClinic.prescription;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "prescription_document_templates")
@Getter @Setter
public class PrescriptionDocumentTemplate {
    @Id private UUID id;
    @Version private long version;
    @Column(nullable = false) private Long organizationId;
    private Long doctorId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 40) private String templateType;
    @Column(nullable = false, length = 20) private String status = "ACTIVE";
    @Column(nullable = false) private boolean isDefault;
    @Column(nullable = false, columnDefinition = "TEXT") private String configuration;
    @Column(nullable = false) private Instant createdAt = Instant.now();
    @Column(nullable = false) private Instant updatedAt = Instant.now();
}
