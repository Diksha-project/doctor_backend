package com.doctor.clinic.DoctorClinic.request;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class BasicDoctorProfileUpdateRequest {
    @NotBlank private String fullName;
    @NotBlank @Email private String email;
    @NotBlank @Pattern(regexp = "^[6-9]\\d{9}$") private String phoneNumber;
    @NotBlank private String address;
}
