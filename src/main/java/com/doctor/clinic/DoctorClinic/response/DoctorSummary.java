package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;



@Data
@Builder
public class DoctorSummary {
	
	
	private Long doctorId;
    private String doctorName;
    private String specialization;
    private String qualification;
    private Integer experienceYears;
    private String phoneNumber;
    private String email;
    private String status;  // ACTIVE, INACTIVE
    private Integer todayAppointments;
    private Integer completedAppointments;
    private Integer availableSlots;
    private BigDecimal todayEarnings;
    private String profileImage;

}
