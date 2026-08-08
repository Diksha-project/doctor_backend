package com.doctor.clinic.DoctorClinic.repo;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import com.doctor.clinic.DoctorClinic.entity.Doctor;

import autovalue.shaded.com.google.common.base.Optional;


@Repository
public interface DoctorRepo extends JpaRepository<Doctor, Long> {
	Doctor findByPhoneNumber (String doctorPhoneNumber);

	List<Doctor> findByOrganizationId(Long organizationId);
	
	
	Doctor findByEmail(String email);




}
