package com.doctor.clinic.DoctorClinic.request;

import java.time.DayOfWeek;
import java.time.LocalTime;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * A single recurring weekly availability period, e.g. Monday 09:00-13:00.
 */
@Data
public class AvailabilityPeriodRequest {

    @NotNull(message = "dayOfWeek is required")
    private DayOfWeek dayOfWeek;

    @NotNull(message = "startTime is required")
    private LocalTime startTime;

    @NotNull(message = "endTime is required")
    private LocalTime endTime;
}
