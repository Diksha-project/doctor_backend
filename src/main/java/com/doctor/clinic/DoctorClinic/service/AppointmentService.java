package com.doctor.clinic.DoctorClinic.service;

import java.util.Map;

import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateStatusRequest;
import com.doctor.clinic.DoctorClinic.response.AppointmentDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.BookAppointmentResponse;

import jakarta.validation.Valid;

public interface AppointmentService {

	BookAppointmentResponse bookAppointment(@Valid BookAppointmentRequest request);

	BookAppointmentResponse confirmPayment(Long appointmentId, String paymentId);


    Map<String, Object> updateAppointmentStatus(UpdateStatusRequest request);

	AppointmentDashboardResponse getAppointmentDashboard(Long doctorId);

}
