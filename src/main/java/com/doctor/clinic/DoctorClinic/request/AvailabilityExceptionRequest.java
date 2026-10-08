package com.doctor.clinic.DoctorClinic.request;

import java.time.LocalDate;
import java.time.LocalTime;

import com.doctor.clinic.DoctorClinic.model.AvailabilityExceptionType;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Creates or replaces a date-specific override of a doctor's normal
 * schedule: a day off, custom hours for that date, or a custom daily limit.
 */
@Data
public class AvailabilityExceptionRequest {

    @NotNull(message = "date is required")
    private LocalDate date;

    @NotNull(message = "type is required")
    private AvailabilityExceptionType type;

    // Required when type == CUSTOM_HOURS
    private LocalTime startTime;
    private LocalTime endTime;

    // Required when type == CUSTOM_LIMIT
    private Integer maxAppointments;

    private String reason;
}
