package com.doctor.clinic.DoctorClinic.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.DoctorAvailabilitySettings;

@Repository
public interface DoctorAvailabilitySettingsRepo extends JpaRepository<DoctorAvailabilitySettings, Long> {

    Optional<DoctorAvailabilitySettings> findByDoctorId(Long doctorId);
}
