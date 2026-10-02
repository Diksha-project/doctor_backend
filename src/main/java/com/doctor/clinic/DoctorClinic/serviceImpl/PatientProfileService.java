package com.doctor.clinic.DoctorClinic.serviceImpl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;

@Service
public class PatientProfileService {

    private final PatientRepo patientRepo;

    public PatientProfileService(PatientRepo patientRepo) {
        this.patientRepo = patientRepo;
    }

    @Transactional
    public Patient upsert(Organization organization, String phone, String name,
                          String email, Integer age, String gender) {
        String normalizedPhone = normalizePhone(phone);
        if (normalizedPhone.isBlank()) {
            throw new IllegalArgumentException("Patient phone number is required");
        }

        Patient patient = patientRepo
                .findByOrganizationIdAndNormalizedPhone(organization.getId(), normalizedPhone)
                .orElseGet(() -> {
                    Patient created = new Patient();
                    created.setOrganization(organization);
                    created.setNormalizedPhone(normalizedPhone);
                    created.setFullName(name == null || name.isBlank() ? "WhatsApp patient" : name.trim());
                    return created;
                });

        if (name != null && !name.isBlank()) patient.setFullName(name.trim());
        if (email != null && !email.isBlank()) patient.setEmail(email.trim());
        if (age != null) patient.setAge(age);
        if (gender != null && !gender.isBlank()) patient.setGender(gender.trim());
        return patientRepo.save(patient);
    }

    public static String normalizePhone(String phone) {
        if (phone == null) return "";
        String digits = phone.replaceAll("\\D", "");
        // The current appointment flow accepts Indian 10-digit numbers; WhatsApp
        // supplies the same numbers with the +91 country prefix.
        if (digits.length() == 12 && digits.startsWith("91")) return digits.substring(2);
        if (digits.length() == 11 && digits.startsWith("0")) return digits.substring(1);
        return digits;
    }
}
