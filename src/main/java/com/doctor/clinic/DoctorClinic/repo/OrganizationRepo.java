package com.doctor.clinic.DoctorClinic.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.Organization;


@Repository
public interface OrganizationRepo extends JpaRepository<Organization, Long> {
	java.util.List<Organization> findAllByOrderByCreatedAtDesc();
	long countByIsActiveTrue();
	
	Optional<Organization> findByOrganizationName(String organizationName);

	Optional<Organization> findByOwnerEmail(String email);

	@Query("SELECT COALESCE(MIN(o.overflowCheckIntervalMinutes), 30) FROM Organization o")
	Integer findMinimumOverflowCheckIntervalMinutes();
	
	

}
