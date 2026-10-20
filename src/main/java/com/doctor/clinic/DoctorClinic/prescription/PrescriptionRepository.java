package com.doctor.clinic.DoctorClinic.prescription;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionRepository extends JpaRepository<Prescription, UUID> {
    Optional<Prescription> findByIdAndOrganizationId(UUID id, Long organizationId);
    Page<Prescription> findByOrganizationIdAndDoctorIdAndPatientId(Long org, Long doctor, Long patient, Pageable page);
    Page<Prescription> findByOrganizationIdAndDoctorId(Long org, Long doctor, Pageable page);
}
