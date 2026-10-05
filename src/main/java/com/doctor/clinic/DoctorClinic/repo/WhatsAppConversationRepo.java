package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppConversation;

public interface WhatsAppConversationRepo extends JpaRepository<WhatsAppConversation, Long> {

    Optional<WhatsAppConversation> findByOrganizationIdAndPatientIdAndPhoneNumber(Long organizationId, Long patientId, String phoneNumber);

    @EntityGraph(attributePaths = {"patient", "doctor", "organization"})
    List<WhatsAppConversation> findByOrganizationIdOrderByLastMessageAtDescCreatedAtDesc(Long organizationId, Pageable pageable);

    @EntityGraph(attributePaths = {"patient", "doctor", "organization"})
    Optional<WhatsAppConversation> findByOrganizationIdAndId(Long organizationId, Long id);

    Optional<WhatsAppConversation> findByOrganizationIdAndPhoneNumber(Long organizationId, String phoneNumber);

    List<WhatsAppConversation> findByOrganizationIdAndPatient(Long organizationId, Patient patient);
}
