package com.doctor.clinic.DoctorClinic.request;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Replaces a doctor's full weekly recurring schedule. Sending an empty list
 * clears the schedule (the doctor will show no availability going forward).
 */
@Data
public class UpdateWeeklyAvailabilityRequest {

    @NotNull(message = "periods is required")
    @Valid
    private List<AvailabilityPeriodRequest> periods;
}
