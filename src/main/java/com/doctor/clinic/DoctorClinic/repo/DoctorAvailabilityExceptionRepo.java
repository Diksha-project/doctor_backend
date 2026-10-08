package com.doctor.clinic.DoctorClinic.repo;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.DoctorAvailabilityException;

@Repository
public interface DoctorAvailabilityExceptionRepo extends JpaRepository<DoctorAvailabilityException, Long> {

    List<DoctorAvailabilityException> findByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAsc(
            Long doctorId, LocalDate startDate, LocalDate endDate);

    List<DoctorAvailabilityException> findByDoctorIdOrderByExceptionDateAsc(Long doctorId);

    Optional<DoctorAvailabilityException> findByDoctorIdAndExceptionDate(Long doctorId, LocalDate exceptionDate);

    void deleteByDoctorIdAndExceptionDate(Long doctorId, LocalDate exceptionDate);
}
