package com.doctor.clinic.DoctorClinic.request;

import java.util.List;
import java.math.BigDecimal;

import lombok.Data;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

@Data
public class UserUpsertRequest {
    private String fullName;
    private String email;
    private String password;
    private Boolean active;
    private List<Long> roleIds;
    private String registrationNumber;
    private String qualification;
    private String specialization;
    @Min(value = 0, message = "Experience cannot be negative")
    @Max(value = 100, message = "Experience cannot exceed 100 years")
    private Integer experienceYears;
    @DecimalMin(value = "0.0", message = "Consultation fee cannot be negative")
    private BigDecimal consultationFee;
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String phoneNumber;
}
