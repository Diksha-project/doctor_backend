package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.Subscription;

import java.util.Optional;


public interface SubscriptionRepo  extends JpaRepository<Subscription, Long>{
	Optional<Subscription> findFirstByOrganizationIdOrderByCreatedAtDesc(Long organizationId);
	long countByStatus(String status);
}
