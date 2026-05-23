package com.doctor.clinic.DoctorClinic.service;

import java.util.Map;

import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.request.WhatsAppBusinessActivateRequest;


public interface DoctorService {
	
	String addNewDoctor(DoctorRegisterRequest req) ;

	Map<String, Object> activateNumber(WhatsAppBusinessActivateRequest request);
	
	

}
