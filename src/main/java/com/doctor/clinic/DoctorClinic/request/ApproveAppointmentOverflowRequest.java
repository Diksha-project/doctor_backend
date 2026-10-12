package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ApproveAppointmentOverflowRequest {
    @NotNull @Min(0) private Integer approvedAdditionalCapacity;
}
