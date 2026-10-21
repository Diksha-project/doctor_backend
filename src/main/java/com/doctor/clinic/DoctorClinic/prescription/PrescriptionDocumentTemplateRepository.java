package com.doctor.clinic.DoctorClinic.prescription;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PrescriptionDocumentTemplateRepository extends JpaRepository<PrescriptionDocumentTemplate, UUID> {
    Optional<PrescriptionDocumentTemplate> findByIdAndOrganizationId(UUID id, Long organizationId);
    List<PrescriptionDocumentTemplate> findByOrganizationIdOrderByIsDefaultDescUpdatedAtDesc(Long organizationId);
    List<PrescriptionDocumentTemplate> findByOrganizationIdAndDoctorIdAndStatusOrderByIsDefaultDescUpdatedAtDesc(Long organizationId, Long doctorId, String status);
    List<PrescriptionDocumentTemplate> findByOrganizationIdAndDoctorIdIsNullAndStatusOrderByIsDefaultDescUpdatedAtDesc(Long organizationId, String status);
}
