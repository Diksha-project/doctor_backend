package com.doctor.clinic.DoctorClinic.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "demo_requests")
@Getter
@Setter
public class DemoRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String organizationName;
    @Column(nullable = false, length = 120)
    private String contactName;
    @Column(length = 254)
    private String email;
    @Column(nullable = false, length = 30)
    private String phone;
    @Column(length = 120)
    private String city;
    private Integer expectedDoctors;
    @Column(length = 1000)
    private String message;
    @Column(nullable = false, length = 30)
    private String status = "NEW";
    @Column(length = 2000)
    private String internalNotes;
    @Column(nullable = false, updatable = false)
    private Instant requestedAt = Instant.now();
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
