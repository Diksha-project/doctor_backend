package com.doctor.clinic.DoctorClinic.request;

import lombok.Data;

@Data
public class PatientMsgRequest {
	
	private String WhatsappNumber;
	
	private String message;

}
