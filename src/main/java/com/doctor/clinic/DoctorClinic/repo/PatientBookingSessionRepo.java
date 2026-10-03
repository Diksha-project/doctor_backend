package com.doctor.clinic.DoctorClinic.repo;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.doctor.clinic.DoctorClinic.entity.PatientBookingSession;

public interface PatientBookingSessionRepo extends JpaRepository<PatientBookingSession, Long> {
    Optional<PatientBookingSession> findByPatientIdAndDoctorId(Long patientId, Long doctorId);
    void deleteByPatientIdAndDoctorId(Long patientId, Long doctorId);
}
