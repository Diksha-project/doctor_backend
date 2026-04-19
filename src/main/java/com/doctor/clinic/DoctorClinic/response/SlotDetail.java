package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class SlotDetail {
	
	private Long appointmentId;
    private Long slotId;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private String status;           // SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW
    private String patientName;
    private String patientPhone;
    private Integer patientAge;
    private String patientGender;
    private String reasonForVisit;
    private String paymentStatus;
    private BigDecimal consultationFee;
    private String slotType; 

}
