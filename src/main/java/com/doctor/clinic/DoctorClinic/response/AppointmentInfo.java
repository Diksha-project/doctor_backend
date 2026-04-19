package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class AppointmentInfo {
	
	private Long appointmentId;
    private LocalTime time;
    private LocalTime endTime;
    private Integer durationMinutes;
    private String patientName;
    private String patientPhone;
    private Integer patientAge;
    private String reasonForVisit;
    private BigDecimal fee;
    private String paymentStatus;

}
