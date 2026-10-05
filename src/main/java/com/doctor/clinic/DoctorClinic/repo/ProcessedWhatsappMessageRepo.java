package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.ProcessedWhatsappMessage;

public interface ProcessedWhatsappMessageRepo extends JpaRepository<ProcessedWhatsappMessage, String>, ProcessedWhatsappMessageCustomRepo {
}
