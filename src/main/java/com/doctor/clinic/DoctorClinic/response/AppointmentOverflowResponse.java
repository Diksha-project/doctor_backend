package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.doctor.clinic.DoctorClinic.model.AppointmentOverflowStatus;

import lombok.Builder;
import lombok.Value;

@Value @Builder
public class AppointmentOverflowResponse {
    Long id; Long userId; String doctorName; LocalDate targetDate;
    Integer requestedAdditionalCapacity; Integer approvedAdditionalCapacity;
    Integer currentBooked; Integer availableSlots; Integer unresolvedPatients;
    AppointmentOverflowStatus status; String reason;
    LocalDateTime deadlineAt; LocalDateTime decidedAt; String decidedBy; LocalDateTime createdAt;
}
