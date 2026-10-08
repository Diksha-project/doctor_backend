package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.BookingRequestEvent;

public interface BookingRequestEventRepo extends JpaRepository<BookingRequestEvent, Long> {

    List<BookingRequestEvent> findByBookingRequestIdOrderByCreatedAtAsc(Long bookingRequestId);
}
