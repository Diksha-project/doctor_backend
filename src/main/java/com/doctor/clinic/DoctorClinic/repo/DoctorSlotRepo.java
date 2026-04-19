package com.doctor.clinic.DoctorClinic.repo;

import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DoctorSlotRepo extends JpaRepository<DoctorSlot, Long> {

	Optional<DoctorSlot> findByDoctorIdAndSlotDateAndStartTime(Long doctorId, LocalDate date, LocalTime time);

	List<DoctorSlot> findByDoctorIdAndSlotDate(Long doctorId, LocalDate date);

	List<DoctorSlot> findByDoctorIdAndSlotDateAndIsAvailableTrue(Long doctorId, LocalDate date);

	@Query("SELECT ds FROM DoctorSlot ds WHERE ds.doctor.id = :doctorId AND ds.slotDate = :date AND ds.startTime >= :startTime AND ds.endTime <= :endTime AND ds.isAvailable = true")
	List<DoctorSlot> findAvailableSlotsInTimeRange(@Param("doctorId") Long doctorId, @Param("date") LocalDate date,
			@Param("startTime") LocalTime startTime, @Param("endTime") LocalTime endTime);

	List<DoctorSlot> findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(Long id, LocalDate today, LocalDate nextWeek);
//
//	@Query("SELECT COUNT(s) FROM DoctorSlot s WHERE s.doctor.id = :doctorId AND s.slotDate = :date AND s.isAvailable = true")
//	int countAvailableSlotsByDoctorIdAndDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

	  @Query("SELECT COUNT(s) FROM DoctorSlot s WHERE s.doctor.id = :doctorId AND s.slotDate = :date AND s.isAvailable = true")
	  int countByDoctorIdAndSlotDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

	
//	  @Query("SELECT COUNT(s) FROM DoctorSlot s WHERE s.doctor.id = :doctorId AND s.slotDate = :date AND s.isAvailable = true")
//	    int countByDoctorIdAndSlotDateAndStatus(@Param("doctorId") Long doctorId, @Param("date") LocalDate date, @Param("status") String status);
	

	    
}