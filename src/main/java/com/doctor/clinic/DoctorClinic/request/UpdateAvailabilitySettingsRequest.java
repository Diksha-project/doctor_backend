package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateAvailabilitySettingsRequest {

    @NotNull(message = "appointmentDurationMinutes is required")
    @Min(value = 5, message = "appointmentDurationMinutes must be at least 5")
    private Integer appointmentDurationMinutes;

    @NotNull(message = "maxAppointmentsPerDay is required")
    @Min(value = 1, message = "maxAppointmentsPerDay must be at least 1")
    private Integer maxAppointmentsPerDay;

    @Min(value = 1, message = "slotGenerationHorizonDays must be at least 1")
    private Integer slotGenerationHorizonDays;
}
