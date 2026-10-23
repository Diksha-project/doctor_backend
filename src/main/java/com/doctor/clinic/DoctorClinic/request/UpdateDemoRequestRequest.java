package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateDemoRequestRequest {
    @NotBlank(message = "Status is required")
    private String status;

    @Size(max = 2000, message = "Internal notes must not exceed 2000 characters")
    private String internalNotes;
}
