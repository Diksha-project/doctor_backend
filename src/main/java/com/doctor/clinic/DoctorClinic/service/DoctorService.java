package com.doctor.clinic.DoctorClinic.service;

import java.util.Map;

import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.request.BasicDoctorProfileUpdateRequest;
import com.doctor.clinic.DoctorClinic.request.WhatsAppBusinessActivateRequest;
import com.doctor.clinic.DoctorClinic.response.DoctorResponse;

import jakarta.validation.Valid;


public interface DoctorService {
	
	String addNewDoctor(DoctorRegisterRequest req) ;

	Map<String, Object> activateNumber(WhatsAppBusinessActivateRequest request);

	DoctorResponse getDoctorDetails(Long userId, Long organizationId);

	DoctorResponse updateBasicProfile(Long userId, Long organizationId, BasicDoctorProfileUpdateRequest request);
	
	

}
