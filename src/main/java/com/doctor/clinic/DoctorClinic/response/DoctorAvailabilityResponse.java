package com.doctor.clinic.DoctorClinic.response;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoctorAvailabilityResponse {
    private Long doctorId;
    private Integer appointmentDurationMinutes;
    private Integer maxAppointmentsPerDay;
    private Integer slotGenerationHorizonDays;
    private List<AvailabilityPeriodResponse> weeklySchedule;
    private List<AvailabilityExceptionResponse> exceptions;
}
