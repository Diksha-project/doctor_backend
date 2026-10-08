package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BookingRequestEventResponse {
    private String eventType;
    private String description;
    private LocalDateTime createdAt;
}
