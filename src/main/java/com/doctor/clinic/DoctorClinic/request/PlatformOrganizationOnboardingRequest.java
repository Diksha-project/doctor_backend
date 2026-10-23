package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.math.BigDecimal;

@Data
public class PlatformOrganizationOnboardingRequest {
    @NotBlank
    @Size(max = 255)
    private String organizationName;

    @NotBlank
    @Size(max = 100)
    private String superAdminName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String superAdminEmail;

    @NotBlank
    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Mobile number must be a valid 10-digit Indian number")
    private String superAdminMobile;

    @NotBlank
    @Size(min = 8, max = 50)
    private String temporaryPassword;

    @Min(1)
    @Max(10000)
    private Integer maxDoctors;

    @Min(1)
    @Max(365)
    private Integer trialDays;

    @NotNull
    private Boolean superAdminIsDoctor = false;

    @Size(max = 255)
    private String doctorRegistrationNumber;

    @Size(max = 255)
    private String doctorQualification;

    @Size(max = 255)
    private String doctorSpecialization;

    @Min(0)
    @Max(100)
    private Integer doctorExperienceYears;

    @DecimalMin(value = "0.0", inclusive = true)
    private BigDecimal doctorConsultationFee;

    @AssertTrue(message = "Doctor registration number is required when the Super Admin is also a doctor")
    public boolean isDoctorDetailsValid() {
        return !Boolean.TRUE.equals(superAdminIsDoctor)
                || doctorRegistrationNumber != null && !doctorRegistrationNumber.isBlank();
    }
}
