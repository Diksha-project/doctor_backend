
package com.doctor.clinic.DoctorClinic.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.doctor.clinic.DoctorClinic.model.ApiResponse;
import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateStatusRequest;
import com.doctor.clinic.DoctorClinic.response.AppointmentDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.BookAppointmentResponse;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;


@Slf4j
@RestController
@RequestMapping("/api/appointments")
@RequiredArgsConstructor
public class AppointmentController {

	private final AppointmentService appointmentService;

	@PostMapping("/book")
	public ResponseEntity<ApiResponse<BookAppointmentResponse>> bookAppointment(
			@Valid @RequestBody BookAppointmentRequest request) {

		log.info("Booking appointment for doctor: {} at {} {}", request.getDoctorId(), request.getAppointmentDate(),
				request.getAppointmentTime());

		try {
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
			BookAppointmentResponse response = appointmentService.confirmPayment(appointmentId, paymentId);
			return ResponseEntity.ok(ApiResponse.success("Payment confirmed", response));
		} catch (Exception e) {
			return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
		}
	}

	    
	   
	    
	 @PatchMapping("/status")
	    public ResponseEntity<Map<String, Object>> updateStatus(@RequestBody UpdateStatusRequest request) {
	        return ResponseEntity.ok(appointmentService.updateAppointmentStatus(request));
	    }
	
	 
	 @GetMapping("/{doctorId}/dashboard")
	    public ResponseEntity<AppointmentDashboardResponse> getDashboard(@PathVariable Long doctorId) {
	        return ResponseEntity.ok(appointmentService.getAppointmentDashboard(doctorId));
	    }
}