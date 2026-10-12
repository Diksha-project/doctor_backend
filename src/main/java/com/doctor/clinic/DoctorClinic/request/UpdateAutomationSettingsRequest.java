package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

@Data
public class UpdateAutomationSettingsRequest {
    @Min(1)
    @Max(1440)
    private Integer overflowCheckIntervalMinutes = 30;

    private Boolean appointmentAutomationEnabled = true;
}
