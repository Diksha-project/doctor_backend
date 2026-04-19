package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AppointmentDashboardResponse {
	
	private Long doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private LocalDate today;
    private String currentTime;
    
    // Status wise appointments
    private List<AppointmentInfo> scheduled;    // Upcoming
    private List<AppointmentInfo> confirmed;   // Confirmed by doctor
    private List<AppointmentInfo> completed;   // Done
    private List<AppointmentInfo> cancelled;   // Cancelled
    private List<AppointmentInfo> noShow;      // Patient didn't come
    
    // Summary
    private AppointmentSummary summary;

}
