package com.doctor.clinic.DoctorClinic.repo;

import com.doctor.clinic.DoctorClinic.entity.Appointment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Repository
public interface AppointmentRepo extends JpaRepository<Appointment, Long> {
    
    // Find by doctor
    List<Appointment> findByDoctorId(Long doctorId);
    

    
    // Find by patient
    List<Appointment> findByPatientPhone(String patientPhone);
    
    List<Appointment> findByPatientPhoneAndAppointmentStatus(String patientPhone, String status);
    
    // Find by status
    List<Appointment> findByAppointmentStatus(String status);
    
    List<Appointment> findByDoctorIdAndAppointmentStatus(Long doctorId, String status);
    
    // Find upcoming appointments
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate >= :today AND a.appointmentStatus IN ('SCHEDULED', 'CONFIRMED') ORDER BY a.appointmentDate ASC, a.appointmentTime ASC")
    List<Appointment> findUpcomingAppointments(@Param("doctorId") Long doctorId, @Param("today") LocalDate today);
    
    // Find today's appointments
    @Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :today ORDER BY a.appointmentTime ASC")
    List<Appointment> findTodaysAppointments(@Param("doctorId") Long doctorId, @Param("today") LocalDate today);
    
    // Find by payment status
    List<Appointment> findByPaymentStatus(String paymentStatus);
    
    // Check for overlapping appointments
    @Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.appointmentTime = :time AND a.appointmentStatus NOT IN ('CANCELLED', 'NO_SHOW')")
    long countConflictingAppointments(@Param("doctorId") Long doctorId, @Param("date") LocalDate date, @Param("time") LocalTime time);

	List<Appointment> findByDoctorIdAndAppointmentDateBetween(Long id, LocalDate today, LocalDate nextWeek);
	


	boolean existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNot(Long id,
			LocalDate appointmentDate, LocalTime appointmentTime, String status);
	
	List<Appointment> findByDoctorIdAndAppointmentDateAndAppointmentStatusNot(Long doctorId, LocalDate date, String status);

	@Query("SELECT a FROM Appointment a WHERE a.doctor.id = :doctorId " +
		       "AND a.appointmentDate = :date " +
		       "AND a.appointmentStatus NOT IN :statuses")
		List<Appointment> findByDoctorIdAndAppointmentDateAndAppointmentStatusNotIn(
		    @Param("doctorId") Long doctorId,
		    @Param("date") LocalDate date,
		    @Param("statuses") List<String> statuses);
	
	List<Appointment> findByDoctorIdAndAppointmentDate(Long doctorId, LocalDate date);



	@Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date")
    int countByDoctorIdAndAppointmentDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
    
    @Query("SELECT COALESCE(SUM(a.consultationFee), 0) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.paymentStatus = 'PAID'")
    BigDecimal sumEarningsByDoctorIdAndDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

}