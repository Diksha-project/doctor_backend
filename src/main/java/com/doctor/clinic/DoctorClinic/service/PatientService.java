package com.doctor.clinic.DoctorClinic.service;

import com.doctor.clinic.DoctorClinic.request.PatientMsgRequest;

public interface PatientService {
	
	Object patientQuery(PatientMsgRequest patientMsgRequest);

}
