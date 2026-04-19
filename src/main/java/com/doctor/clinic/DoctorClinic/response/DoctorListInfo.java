package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class DoctorListInfo {
	
	private Long doctorId;
    private String doctorName;
    private String specialization;
    private String qualification;
    private Integer experienceYears;
    private String phoneNumber;
    private String email;
    private String status;
    private Integer todayAppointments;
    private BigDecimal todayEarnings;
    private Integer availableSlots;

}
