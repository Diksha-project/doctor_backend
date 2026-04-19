package com.doctor.clinic.DoctorClinic.request;



import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class DoctorRegisterRequest {
    
	@NotBlank(message = "Organization name is required")
    private String organizationName;
    
    @NotBlank(message = "First name is required")
    private String firstName;
    
    private String lastName;
    
    @NotBlank(message = "Registration number is required")
    private String registrationNumber;
    
    private String qualification;
    
    private String specialization;
    
    private Integer experienceYears;
    
    @NotBlank(message = "Mobile number is required")
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
    private String phoneNumber;
    
    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;
    
    @NotNull(message = "Consultation fee is required")
    private BigDecimal consultationFee;
    
    @NotBlank(message = "Consultation hours is required")
    private String consultationHours;

}