package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.service.DoctorIdentityService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorIdentityServiceImpl implements DoctorIdentityService {
    private final DoctorRepo doctorRepo;

    @Override
    public Doctor requireDoctor(Long userId, Long organizationId) {
        return findDoctor(userId, organizationId)
                .orElseThrow(() -> new BusinessException(404, "DOCTOR_PROFILE_NOT_FOUND",
                        "Doctor profile not found for this user"));
    }

    @Override
    public Optional<Doctor> findDoctor(Long userId, Long organizationId) {
        if (userId == null || organizationId == null) {
            return Optional.empty();
        }
        return doctorRepo.findByUserIdAndOrganizationId(userId, organizationId);
    }
}
