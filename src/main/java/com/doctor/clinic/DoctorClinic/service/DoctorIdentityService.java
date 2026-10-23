package com.doctor.clinic.DoctorClinic.service;

import java.util.Optional;

import com.doctor.clinic.DoctorClinic.entity.Doctor;

public interface DoctorIdentityService {
    Doctor requireDoctor(Long userId, Long organizationId);

    Optional<Doctor> findDoctor(Long userId, Long organizationId);
}
