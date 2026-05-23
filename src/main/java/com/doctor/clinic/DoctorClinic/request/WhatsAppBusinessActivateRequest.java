package com.doctor.clinic.DoctorClinic.request;

import lombok.Data;

@Data
public class WhatsAppBusinessActivateRequest {

	    private String authorizationCode;
	    private Long doctorId;
	}


