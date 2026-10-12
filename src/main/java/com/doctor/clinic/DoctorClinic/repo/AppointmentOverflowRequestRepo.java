package com.doctor.clinic.DoctorClinic.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import com.doctor.clinic.DoctorClinic.entity.AppointmentOverflowRequest;
import com.doctor.clinic.DoctorClinic.model.AppointmentOverflowStatus;

import jakarta.persistence.LockModeType;

public interface AppointmentOverflowRequestRepo extends JpaRepository<AppointmentOverflowRequest, Long> {
    List<AppointmentOverflowRequest> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
    List<AppointmentOverflowRequest> findByOrganizationIdAndDoctorIdOrderByCreatedAtDesc(Long organizationId, Long doctorId);
    List<AppointmentOverflowRequest> findByDoctorIdAndTargetDateOrderByCreatedAtDesc(Long doctorId, java.time.LocalDate targetDate);
    Optional<AppointmentOverflowRequest> findByIdAndOrganizationId(Long id, Long organizationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from AppointmentOverflowRequest r where r.id = :id and r.organization.id = :organizationId")
    Optional<AppointmentOverflowRequest> findByIdAndOrganizationIdForUpdate(@Param("id") Long id,
            @Param("organizationId") Long organizationId);
    List<AppointmentOverflowRequest> findByStatusAndDeadlineAtBefore(AppointmentOverflowStatus status, LocalDateTime now);

    @Query("select coalesce(sum(r.approvedAdditionalCapacity), 0) from AppointmentOverflowRequest r "
            + "where r.doctor.id = :doctorId and r.targetDate = :date "
            + "and r.status in ('APPROVED', 'PARTIALLY_APPROVED')")
    int sumApprovedCapacity(@Param("doctorId") Long doctorId, @Param("date") java.time.LocalDate date);
}
