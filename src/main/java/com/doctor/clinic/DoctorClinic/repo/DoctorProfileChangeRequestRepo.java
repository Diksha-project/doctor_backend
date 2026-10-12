package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import com.doctor.clinic.DoctorClinic.entity.DoctorProfileChangeRequest;
import com.doctor.clinic.DoctorClinic.model.DoctorProfileChangeStatus;
import jakarta.persistence.LockModeType;

public interface DoctorProfileChangeRequestRepo extends JpaRepository<DoctorProfileChangeRequest, Long> {
    List<DoctorProfileChangeRequest> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId, DoctorProfileChangeStatus status);
    List<DoctorProfileChangeRequest> findByOrganizationIdAndDoctorIdOrderByCreatedAtDesc(Long organizationId, Long doctorId);
    List<DoctorProfileChangeRequest> findByOrganizationIdAndDoctorIdAndStatusOrderByCreatedAtDesc(Long organizationId, Long doctorId, DoctorProfileChangeStatus status);
    List<DoctorProfileChangeRequest> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    boolean existsByOrganizationIdAndDoctorIdAndStatus(Long organizationId, Long doctorId, DoctorProfileChangeStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<DoctorProfileChangeRequest> findByIdAndOrganizationId(Long id, Long organizationId);
}
