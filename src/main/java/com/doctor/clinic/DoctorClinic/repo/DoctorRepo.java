package com.doctor.clinic.DoctorClinic.repo;


import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import com.doctor.clinic.DoctorClinic.entity.Doctor;

import jakarta.persistence.LockModeType;

import java.util.Optional;


@Repository
public interface DoctorRepo extends JpaRepository<Doctor, Long> {
	Doctor findByPhoneNumber (String doctorPhoneNumber);
	
	java.util.Optional<Doctor> findByWhatsappNumber(String whatsappNumber);

	List<Doctor> findByOrganizationId(Long organizationId);

	Optional<Doctor> findByIdAndOrganizationId(Long doctorId, Long organizationId);
	
	
	Doctor findByEmail(String email);

	List<Doctor> findByStatus(String status);

	// Locks the doctor row for the duration of the transaction so concurrent
	// booking requests for the same doctor are serialized, preventing
	// double-booking / daily-limit race conditions.
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT d FROM Doctor d WHERE d.id = :id")
	Optional<Doctor> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") Long id);

}
