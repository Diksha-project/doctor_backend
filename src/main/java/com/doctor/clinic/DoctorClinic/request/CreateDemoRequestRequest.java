package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateDemoRequestRequest {
    @NotBlank(message = "Organization name is required")
    @Size(max = 150, message = "Organization name must not exceed 150 characters")
    private String organizationName;

    @NotBlank(message = "Contact name is required")
    @Size(max = 120, message = "Contact name must not exceed 120 characters")
    private String contactName;

    @Email(message = "Email must be valid")
    @Size(max = 254, message = "Email must not exceed 254 characters")
    private String email;

    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^[+0-9 ()-]{8,30}$", message = "Phone number must be valid")
    private String phone;

    @Size(max = 120, message = "City must not exceed 120 characters")
    private String city;

    @Min(value = 1, message = "Expected doctors must be at least 1")
    @Max(value = 10000, message = "Expected doctors must not exceed 10000")
    private Integer expectedDoctors;

    @Size(max = 1000, message = "Message must not exceed 1000 characters")
    private String message;
}
