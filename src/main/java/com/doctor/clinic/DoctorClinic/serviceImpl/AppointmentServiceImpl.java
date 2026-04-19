package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateStatusRequest;
import com.doctor.clinic.DoctorClinic.response.AppointmentDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.AppointmentInfo;
import com.doctor.clinic.DoctorClinic.response.AppointmentSummary;
import com.doctor.clinic.DoctorClinic.response.BookAppointmentResponse;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;

import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class AppointmentServiceImpl implements AppointmentService {

	@Autowired
	private AppointmentRepo appointmentRepo;

	@Autowired
	private DoctorRepo doctorRepo;

	@Autowired
	private DoctorSlotRepo doctorSlotRepo;

	@Transactional
	public BookAppointmentResponse bookAppointment(BookAppointmentRequest request) {

		// 1. Validate doctor exists
		Doctor doctor = doctorRepo.findById(request.getDoctorId())
				.orElseThrow(() -> new RuntimeException("Doctor not found with ID: " + request.getDoctorId()));

		// 2. Check if doctor is active
		if (!"ACTIVE".equals(doctor.getStatus())) {
			throw new RuntimeException("Doctor is not available for appointments");
		}

		// 3. Get doctor's default slot duration (5, 15, 30, 60 minutes)
		Integer slotDuration = doctor.getDefaultSlotDurationMinutes();
		if (slotDuration == null) {
			slotDuration = 30; // Default fallback to 30 minutes
		}

		// 4. Calculate end time based on slot duration
		LocalTime endTime = request.getAppointmentTime().plusMinutes(slotDuration);

		// 5. Check for conflicting appointment (including overlapping slots)
		boolean hasConflict = appointmentRepo
				.existsByDoctorIdAndAppointmentDateAndAppointmentTimeAndAppointmentStatusNot(doctor.getId(),
						request.getAppointmentDate(), request.getAppointmentTime(), "CANCELLED");

		if (hasConflict) {
			throw new RuntimeException("Time slot already booked. Please choose another time.");
		}

		// 6. Check for overlapping appointments (if slot duration is longer than
		// standard)
		boolean hasOverlap = checkOverlappingAppointments(doctor.getId(), request.getAppointmentDate(),
				request.getAppointmentTime(), endTime);

		if (hasOverlap) {
			throw new RuntimeException("This time overlaps with another appointment. Please choose another time.");
		}

		// 7. Check and update slot availability
		DoctorSlot slot = doctorSlotRepo.findByDoctorIdAndSlotDateAndStartTime(doctor.getId(),
				request.getAppointmentDate(), request.getAppointmentTime()).orElse(null);

		if (slot != null && !slot.hasAvailability()) {
			throw new RuntimeException("This time slot is fully booked. Please choose another time.");
		}

		// Update slot booked count (if slot exists)
		if (slot != null) {
			slot.setBookedCount(slot.getBookedCount() + 1);
			slot.setEndTime(endTime);
			slot.setDurationMinutes(slotDuration);
			doctorSlotRepo.save(slot);
		} else {
			// Create a new slot entry for this booking
			DoctorSlot newSlot = DoctorSlot.builder().doctor(doctor).organization(doctor.getOrganization())
					.slotDate(request.getAppointmentDate()).startTime(request.getAppointmentTime()).endTime(endTime)
					.durationMinutes(slotDuration).isAvailable(false).maxAppointments(1).bookedCount(1)
					.slotType("BOOKED").build();
			doctorSlotRepo.save(newSlot);
		}

		// 8. Calculate final amount
		BigDecimal finalAmount = doctor.getConsultationFee();

		// 9. Create appointment with dynamic slot duration
		Appointment appointment = Appointment.builder().doctor(doctor).organization(doctor.getOrganization())
				.patientName(request.getPatientName()).patientPhone(request.getPatientPhone())
				.patientEmail(request.getPatientEmail()).patientAge(request.getPatientAge())
				.patientGender(request.getPatientGender()).appointmentDate(request.getAppointmentDate())
				.appointmentTime(request.getAppointmentTime()).endTime(endTime).slotDurationMinutes(slotDuration)
				.consultationFee(doctor.getConsultationFee()).finalAmount(finalAmount).paymentStatus("PENDING")
				.paymentMethod(request.getPaymentMethod()).appointmentStatus("SCHEDULED")
				.reasonForVisit(request.getReasonForVisit()).symptoms(request.getSymptoms()).createdBy("PATIENT")
				.build();

		Appointment savedAppointment = appointmentRepo.save(appointment);

		log.info("Appointment booked successfully. ID: {}, Doctor: {}, Duration: {} min, Time: {} {}",
				savedAppointment.getId(), doctor.getFullName(), slotDuration, request.getAppointmentDate(),
				request.getAppointmentTime());

		// 10. Build response
		return BookAppointmentResponse.builder().appointmentId(savedAppointment.getId())
				.doctorName(doctor.getFullName()).doctorSpecialization(doctor.getSpecialization())
				.patientName(request.getPatientName()).patientPhone(request.getPatientPhone())
				.appointmentDate(request.getAppointmentDate()).appointmentTime(request.getAppointmentTime())
				.endTime(endTime).slotDurationMinutes(slotDuration).consultationFee(doctor.getConsultationFee())
				.finalAmount(finalAmount).paymentStatus("PENDING").appointmentStatus("SCHEDULED")
				.createdAt(savedAppointment.getCreatedAt()).message(String
						.format("Appointment booked for %d minutes! Please complete payment to confirm.", slotDuration))
				.build();
	}

	@Transactional
	public BookAppointmentResponse confirmPayment(Long appointmentId, String paymentId) {
		Appointment appointment = appointmentRepo.findById(appointmentId)
				.orElseThrow(() -> new RuntimeException("Appointment not found"));

		appointment.setPaymentStatus("PAID");
		appointment.setPaymentId(paymentId);
		appointment.setPaymentDate(LocalDateTime.now());
		appointment.setAppointmentStatus("CONFIRMED");

		appointmentRepo.save(appointment);

		return BookAppointmentResponse.builder().appointmentId(appointment.getId()).appointmentStatus("CONFIRMED")
				.paymentStatus("PAID").message("Payment confirmed! Appointment is now confirmed.").build();
	}

	@Transactional
	public void cancelAppointment(Long appointmentId, String reason) {
		Appointment appointment = appointmentRepo.findById(appointmentId)
				.orElseThrow(() -> new RuntimeException("Appointment not found"));

		// Release the slot
		DoctorSlot slot = doctorSlotRepo.findByDoctorIdAndSlotDateAndStartTime(appointment.getDoctor().getId(),
				appointment.getAppointmentDate(), appointment.getAppointmentTime()).orElse(null);

		if (slot != null && slot.getBookedCount() > 0) {
			slot.setBookedCount(slot.getBookedCount() - 1);

			// If no more bookings, mark as available
			if (slot.getBookedCount() == 0) {
				slot.setAvailable(true);
				slot.setSlotType("REGULAR");
			}
			doctorSlotRepo.save(slot);
		}

		appointment.setAppointmentStatus("CANCELLED");
		appointment.setCancellationReason(reason);
		appointment.setCancelledBy("PATIENT");
		appointment.setCancelledAt(LocalDateTime.now());

		appointmentRepo.save(appointment);

		log.info("Appointment cancelled. ID: {}, Reason: {}", appointmentId, reason);
	}

	// Helper method to check overlapping appointments
	private boolean checkOverlappingAppointments(Long doctorId, LocalDate date, LocalTime startTime,
			LocalTime endTime) {
		List<Appointment> appointments = appointmentRepo
				.findByDoctorIdAndAppointmentDateAndAppointmentStatusNot(doctorId, date, "CANCELLED");

		for (Appointment existing : appointments) {
			LocalTime existingStart = existing.getAppointmentTime();
			LocalTime existingEnd = existing.getEndTime() != null ? existing.getEndTime()
					: existingStart.plusMinutes(existing.getSlotDurationMinutes());

			// Check if time ranges overlap
			if (startTime.isBefore(existingEnd) && endTime.isAfter(existingStart)) {
				return true;
			}
		}
		return false;
	}

	private static final String[] ALLOWED_STATUS = { "SCHEDULED", "CONFIRMED", "COMPLETED", "CANCELLED", "NO_SHOW" };

	@Transactional
	@Override
	public Map<String, Object> updateAppointmentStatus(UpdateStatusRequest request) {

		// Allowed status values

		// 1. Validate status
		boolean isValid = false;
		for (String status : ALLOWED_STATUS) {
			if (status.equalsIgnoreCase(request.getStatus())) {
				isValid = true;
				break;
			}
		}

		if (!isValid) {
			throw new RuntimeException("Invalid status. Allowed: SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW");
		}

		// 2. Find appointment
		Appointment appointment = appointmentRepo.findById(request.getAppointmentId())
				.orElseThrow(() -> new RuntimeException("Appointment not found"));

		String oldStatus = appointment.getAppointmentStatus();
		String newStatus = request.getStatus().toUpperCase();

		// 3. Validate cancellation reason
		if ((newStatus.equals("CANCELLED") || newStatus.equals("NO_SHOW"))
				&& (request.getCancellationReason() == null || request.getCancellationReason().isEmpty())) {
			throw new RuntimeException("Cancellation reason is required");
		}

		// 4. Update status
		appointment.setAppointmentStatus(newStatus);

		if (newStatus.equals("CANCELLED") || newStatus.equals("NO_SHOW")) {
			appointment.setCancellationReason(request.getCancellationReason());
			appointment.setCancelledBy("DOCTOR");
			appointment.setCancelledAt(LocalDateTime.now());

			// Release slot if cancelled
			doctorSlotRepo.findByDoctorIdAndSlotDateAndStartTime(appointment.getDoctor().getId(),
					appointment.getAppointmentDate(), appointment.getAppointmentTime()).ifPresent(slot -> {
						if (slot.getBookedCount() > 0) {
							slot.setBookedCount(slot.getBookedCount() - 1);
							doctorSlotRepo.save(slot);
						}
					});
		}

		if (newStatus.equals("CONFIRMED")) {
			appointment.setPaymentStatus("PAID");
		}

		appointmentRepo.save(appointment);

		// 5. Response
		Map<String, Object> response = new HashMap<>();
		response.put("appointmentId", appointment.getId());
		response.put("oldStatus", oldStatus);
		response.put("newStatus", newStatus);
		response.put("message", "Status updated successfully");
		response.put("updatedAt", LocalDateTime.now());

		return response;
	}

	@Override
	public AppointmentDashboardResponse getAppointmentDashboard(Long doctorId) {

		Doctor doctor = doctorRepo.findById(doctorId).orElseThrow(() -> new RuntimeException("Doctor not found"));

		LocalDate today = LocalDate.now();
		LocalTime now = LocalTime.now();

		// Get all today's appointments
		List<Appointment> todayAppointments = appointmentRepo.findByDoctorIdAndAppointmentDate(doctorId, today);

		// Separate by status
		List<AppointmentInfo> scheduled = todayAppointments.stream()
				.filter(a -> "SCHEDULED".equals(a.getAppointmentStatus())).map(this::mapToAppointmentInfo)
				.collect(Collectors.toList());

		List<AppointmentInfo> confirmed = todayAppointments.stream()
				.filter(a -> "CONFIRMED".equals(a.getAppointmentStatus())).map(this::mapToAppointmentInfo)
				.collect(Collectors.toList());

		List<AppointmentInfo> completed = todayAppointments.stream()
				.filter(a -> "COMPLETED".equals(a.getAppointmentStatus())).map(this::mapToAppointmentInfo)
				.collect(Collectors.toList());

		List<AppointmentInfo> cancelled = todayAppointments.stream()
				.filter(a -> "CANCELLED".equals(a.getAppointmentStatus())).map(this::mapToAppointmentInfo)
				.collect(Collectors.toList());

		List<AppointmentInfo> noShow = todayAppointments.stream()
				.filter(a -> "NO_SHOW".equals(a.getAppointmentStatus())).map(this::mapToAppointmentInfo)
				.collect(Collectors.toList());

		// Calculate today's earnings
		BigDecimal todayEarnings = completed.stream().filter(a -> "PAID".equals(a.getPaymentStatus()))
				.map(AppointmentInfo::getFee).reduce(BigDecimal.ZERO, BigDecimal::add);

		// Build summary
		AppointmentSummary summary = AppointmentSummary.builder().totalScheduled(scheduled.size())
				.totalConfirmed(confirmed.size()).totalCompleted(completed.size()).totalCancelled(cancelled.size())
				.totalNoShow(noShow.size()).totalPatients(todayAppointments.size()).todayEarnings(todayEarnings)
				.build();

		return AppointmentDashboardResponse.builder().doctorId(doctor.getId()).doctorName(doctor.getFullName())
				.doctorSpecialization(doctor.getSpecialization()).today(today).currentTime(now.toString())
				.scheduled(scheduled).confirmed(confirmed).completed(completed).cancelled(cancelled).noShow(noShow)
				.summary(summary).build();
	}

	private AppointmentInfo mapToAppointmentInfo(Appointment a) {
		LocalTime endTime = a.getEndTime() != null ? a.getEndTime()
				: a.getAppointmentTime().plusMinutes(a.getSlotDurationMinutes());

		return AppointmentInfo.builder().appointmentId(a.getId()).time(a.getAppointmentTime()).endTime(endTime)
				.durationMinutes(a.getSlotDurationMinutes()).patientName(a.getPatientName())
				.patientPhone(a.getPatientPhone()).patientAge(a.getPatientAge()).reasonForVisit(a.getReasonForVisit())
				.fee(a.getConsultationFee()).paymentStatus(a.getPaymentStatus()).build();
	}
}
