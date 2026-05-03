package com.doctor.clinic.DoctorClinic.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
	
	private String token;
    private String email;
    private String role;
    private Long organizationId;
    private String organizationName;
    private String message;

}
