package com.doctor.clinic.DoctorClinic.service;


import com.doctor.clinic.DoctorClinic.response.DoctorSlotsDashboardResponse;


public interface DoctorSlotsService {
	
	//private SlotDetail mapToSlotDetail(Appointment appointment) ;
	
	 public DoctorSlotsDashboardResponse getDoctorSlotsDashboard(Long doctorId);

}
