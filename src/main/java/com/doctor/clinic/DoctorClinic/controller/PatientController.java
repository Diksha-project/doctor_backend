package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.request.PatientMsgRequest;

import com.doctor.clinic.DoctorClinic.AIServices.GeminiService;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/patient")
@RequiredArgsConstructor
public class PatientController {

	private final DoctorRepo doctorRepo;
	private final GeminiService aiService;

	
	@PostMapping("/query")
	public Map<String, Object> query(@RequestBody PatientMsgRequest request) {

		Map<String, Object> response = new HashMap<>();

		// Find doctor by WhatsApp number
		Doctor doctor = doctorRepo.findByPhoneNumber(request.getWhatsappNumber());

		if (doctor == null) {
			response.put("success", false);
			response.put("message", "Doctor not found");
			return response;
		}

		// Generate AI response
		String aiResponse = aiService.generateResponse(request.getMessage(), doctor);

		response.put("success", true);
		response.put("response", aiResponse);
		response.put("doctorName", doctor.getFullName());

		return response;
	}
}
