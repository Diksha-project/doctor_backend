package com.doctor.clinic.DoctorClinic.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.Organization;


@Repository
public interface OrganizationRepo extends JpaRepository<Organization, Long> {
	
	Optional<Organization> findByOrganizationName(String organizationName);
	
	

}
