package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.WhatsAppResponseRule;

public interface WhatsAppResponseRuleRepo extends JpaRepository<WhatsAppResponseRule, Long> {

    List<WhatsAppResponseRule> findByOrganizationIdAndActiveTrueOrderByPriorityDesc(Long organizationId);

    List<WhatsAppResponseRule> findByOrganizationIdAndDoctorIdAndActiveTrueOrderByPriorityDesc(Long organizationId, Long doctorId);
}
