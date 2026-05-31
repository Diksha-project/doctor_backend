package com.doctor.clinic.DoctorClinic.response;


import java.math.BigDecimal;
import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DoctorResponse {
	
	    private Long id;
	    private String firstName;
	    private String lastName;
	    private String registrationNumber;
	    private String qualification;
	    private String specialization;
	    private Integer experienceYears;
	    private String phoneNumber;
	    private String email;
	    private BigDecimal consultationFee;
	    private String consultationHours;
	    private String status;
	    private Integer defaultSlotDurationMinutes;

	    private String whatsappPhoneNumberId;
	    private String whatsappAccessToken;
	    private String whatsappNumber;
	    private boolean whatsappActivated;

	    private LocalDateTime createdAt;
	    private LocalDateTime updatedAt;

	    private Long organizationId;
	    private String organizationName;

}
