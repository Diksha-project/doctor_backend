package com.doctor.clinic.DoctorClinic.request;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateAppointmentOverflowRequest {
    @NotNull private Long doctorId;
    @NotNull private LocalDate targetDate;
    @NotNull @Min(1) private Integer requestedAdditionalCapacity;
    private String reason;
    @Min(1) private Integer deadlineMinutes;
}
