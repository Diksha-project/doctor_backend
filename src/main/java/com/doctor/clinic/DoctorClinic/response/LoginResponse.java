package com.doctor.clinic.DoctorClinic.response;

import java.util.Set;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {
	
	private String token;
    private String email;
    private String role;
    private Set<String> roles;
    private Set<String> permissions;
    private Long userId;
    private Long doctorId;
    private Long organizationId;
    private String organizationName;
    private String name;
    private Boolean active;
    private String message;

}
