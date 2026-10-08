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
import java.util.Collection;
import org.springframework.data.domain.Pageable;

@Repository
public interface AppointmentRepo extends JpaRepository<Appointment, Long> {

    @Query("SELECT a FROM Appointment a LEFT JOIN a.patient p WHERE a.organization.id = :organizationId "
            + "AND (p.id = :patientId OR a.patientPhone IN :phoneAliases) "
            + "ORDER BY a.appointmentDate DESC, a.appointmentTime DESC")
    List<Appointment> findPatientHistory(@Param("organizationId") Long organizationId,
                                         @Param("patientId") Long patientId,
                                         @Param("phoneAliases") Collection<String> phoneAliases);

    List<Appointment> findTop100ByPatientIsNullAndIdGreaterThanOrderByIdAsc(Long id);

    List<Appointment> findByDoctorIdAndOrganizationIdAndAppointmentDateOrderByAppointmentTimeAsc(
            Long doctorId, Long organizationId, LocalDate appointmentDate);

    @Query("SELECT a FROM Appointment a LEFT JOIN a.patient p WHERE a.organization.id = :organizationId "
            + "AND (p.id = :patientId OR a.patientPhone IN :phoneAliases) "
            + "AND (a.appointmentDate < :date OR (a.appointmentDate = :date AND a.appointmentTime < :time)) "
            + "ORDER BY a.appointmentDate DESC, a.appointmentTime DESC")
    List<Appointment> findPreviousPatientVisits(@Param("organizationId") Long organizationId,
                                                @Param("patientId") Long patientId,
                                                @Param("phoneAliases") Collection<String> phoneAliases,
                                                @Param("date") LocalDate date,
                                                @Param("time") LocalTime time,
                                                Pageable pageable);
    
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

	// Used to enforce the doctor's max-appointments-per-day availability setting;
	// cancelled/no-show appointments free up the daily quota.
	@Query("SELECT COUNT(a) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.appointmentStatus NOT IN ('CANCELLED', 'NO_SHOW')")
	long countActiveByDoctorIdAndAppointmentDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
    
    @Query("SELECT COALESCE(SUM(a.consultationFee), 0) FROM Appointment a WHERE a.doctor.id = :doctorId AND a.appointmentDate = :date AND a.paymentStatus = 'PAID'")
    BigDecimal sumEarningsByDoctorIdAndDate(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

}
