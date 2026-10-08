package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.BookingRequest;
import com.doctor.clinic.DoctorClinic.model.BookingRequestStatus;

public interface BookingRequestRepo extends JpaRepository<BookingRequest, Long> {

    List<BookingRequest> findByOrganizationIdOrderByCreatedAtDesc(Long organizationId);

    List<BookingRequest> findByOrganizationIdAndStatusOrderByCreatedAtDesc(Long organizationId,
            BookingRequestStatus status);

    Optional<BookingRequest> findByIdAndOrganizationId(Long id, Long organizationId);

    Optional<BookingRequest> findFirstByPatientIdAndDoctorIdAndStatus(Long patientId, Long doctorId,
            BookingRequestStatus status);
}
