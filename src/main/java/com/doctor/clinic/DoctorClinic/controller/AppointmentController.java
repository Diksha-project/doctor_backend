
package com.doctor.clinic.DoctorClinic.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.time.LocalDate;

import com.doctor.clinic.DoctorClinic.model.ApiResponse;
import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateStatusRequest;
import com.doctor.clinic.DoctorClinic.response.AppointmentDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.BookAppointmentResponse;
import com.doctor.clinic.DoctorClinic.response.DoctorPatientDashboardResponse;
import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;
import com.doctor.clinic.DoctorClinic.serviceImpl.DoctorPatientDashboardService;


@Slf4j
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

	private final AppointmentService appointmentService;
	private final DoctorPatientDashboardService doctorPatientDashboardService;
	private final DoctorRepo doctorRepo;
	private final AppointmentRepo appointmentRepo;

	@PostMapping("/book")
	public ResponseEntity<ApiResponse<BookAppointmentResponse>> bookAppointment(
			@Valid @RequestBody BookAppointmentRequest request) {

		log.info("Booking appointment for doctor: {} at {} {}", request.getDoctorId(), request.getAppointmentDate(),
				request.getAppointmentTime());

		try {
			requireDoctorInOrganization(request.getDoctorId(), organizationId());
			BookAppointmentResponse response = appointmentService.bookAppointment(request);
			return ResponseEntity.ok(ApiResponse.success("Appointment booked successfully", response));
		} catch (Exception e) {
			log.error("Appointment booking failed: {}", e.getMessage());
			return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
		}
	}

	@PostMapping("/{appointmentId}/confirm-payment")
	public ResponseEntity<ApiResponse<BookAppointmentResponse>> confirmPayment(@PathVariable Long appointmentId,
			@RequestParam String paymentId) {

		try {
			requireAppointmentInOrganization(appointmentId, organizationId());
			BookAppointmentResponse response = appointmentService.confirmPayment(appointmentId, paymentId);
			return ResponseEntity.ok(ApiResponse.success("Payment confirmed", response));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
		}
	}

	    
	   
	    
	 @PatchMapping("/status")
	    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody UpdateStatusRequest request) {
	        requireAppointmentInOrganization(request.getAppointmentId(), organizationId());
	        return ResponseEntity.ok(appointmentService.updateAppointmentStatus(request));
	    }
	
	 
	 @GetMapping("/{doctorId}/dashboard")
	    public ResponseEntity<AppointmentDashboardResponse> getDashboard(@PathVariable Long doctorId) {
	        requireDoctorInOrganization(doctorId, organizationId());
	        return ResponseEntity.ok(appointmentService.getAppointmentDashboard(doctorId));
	    }

	@GetMapping("/{doctorId}/dashboard/patients")
	public ResponseEntity<DoctorPatientDashboardResponse> getPatientDashboard(
			@PathVariable Long doctorId,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		LocalDate dashboardDate = date == null ? LocalDate.now() : date;
		return ResponseEntity.ok(doctorPatientDashboardService.getPatientsForDate(
				doctorId, organizationId(), dashboardDate));
	}

	private Long organizationId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		Object details = authentication == null ? null : authentication.getDetails();
		if (details instanceof Number number) return number.longValue();
		throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Organization is missing from access token");
	}

	private void requireDoctorInOrganization(Long doctorId, Long organizationId) {
		if (doctorId == null || doctorRepo.findByIdAndOrganizationId(doctorId, organizationId).isEmpty()) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found");
		}
	}

	private Appointment requireAppointmentInOrganization(Long appointmentId, Long organizationId) {
		Appointment appointment = appointmentId == null ? null : appointmentRepo.findById(appointmentId).orElse(null);
		if (appointment == null || appointment.getOrganization() == null
				|| !organizationId.equals(appointment.getOrganization().getId())) {
			throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
		}
		return appointment;
	}
}
