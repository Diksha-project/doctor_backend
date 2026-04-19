package com.doctor.clinic.DoctorClinic.request;

import lombok.Data;

@Data
public class UpdateStatusRequest {
	
	private Long appointmentId;
    private String status;  // SCHEDULED, CONFIRMED, COMPLETED, CANCELLED, NO_SHOW
    private String cancellationReason;

}
