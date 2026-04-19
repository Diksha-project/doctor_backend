package com.doctor.clinic.DoctorClinic.serviceImpl;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.service.DoctorService;

@Service
public class DoctorServiceImpl implements DoctorService {
    
	@Autowired
    private DoctorRepo doctorRepo;
    
    @Autowired
    private OrganizationRepo organizationRepo;

    @Override
    public String addNewDoctor(DoctorRegisterRequest req) {
        Organization organization = organizationRepo.findByOrganizationName(req.getOrganizationName())
            .orElseThrow(() -> new RuntimeException("Organization not found with name: " + req.getOrganizationName()));
        
        Doctor doctor = Doctor.builder()
            .firstName(req.getFirstName())
            .lastName(req.getLastName())
            .registrationNumber(req.getRegistrationNumber())
            .qualification(req.getQualification())
            .specialization(req.getSpecialization())
            .experienceYears(req.getExperienceYears())
            .phoneNumber(req.getPhoneNumber())
            .email(req.getEmail())
            .consultationFee(req.getConsultationFee())
            .consultationHours(req.getConsultationHours())
            .organization(organization)
            .status("ACTIVE")
            .build();

        Doctor savedDoctor = doctorRepo.save(doctor);
        
        organization.incrementDoctorCount();
        organizationRepo.save(organization);

        return "Doctor added successfully with ID: " + savedDoctor.getId();
    }
}
