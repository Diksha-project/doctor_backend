package com.doctor.clinic.DoctorClinic.request;

import lombok.Data;

@Data
public class LoginRequest {
	private String email;
    private String password;

}
