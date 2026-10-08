package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.time.LocalTime;

import com.doctor.clinic.DoctorClinic.model.AvailabilityExceptionType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AvailabilityExceptionResponse {
    private Long id;
    private LocalDate date;
    private AvailabilityExceptionType type;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer maxAppointments;
    private String reason;
}
