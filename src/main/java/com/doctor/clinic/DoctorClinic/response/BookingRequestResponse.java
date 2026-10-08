package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import com.doctor.clinic.DoctorClinic.model.BookingRequestStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookingRequestResponse {
    private Long id;
    private Long doctorId;
    private String doctorName;
    private String patientName;
    private String patientPhone;
    private String appointmentType;
    private LocalDate requestedDate;
    private LocalTime requestedTime;
    private String reason;
    private String source;
    private BookingRequestStatus status;
    private String note;
    private LocalDateTime createdAt;
}
