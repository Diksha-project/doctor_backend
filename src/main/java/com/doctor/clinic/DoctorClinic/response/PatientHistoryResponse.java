package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record PatientHistoryResponse(
        PatientProfile patient,
        List<AppointmentRecord> appointments,
        List<ChatRecord> conversations) {

    public record PatientProfile(Long id, String fullName, String phone, String email,
                                 Integer age, String gender) {}

    public record AppointmentRecord(Long id, String doctorName, LocalDate date, LocalTime time,
                                    String status, String reason, String symptoms, String notes,
                                    BigDecimal fee, String paymentStatus) {}

    public record ChatRecord(Long id, String direction, String type, String text,
                             String attachmentMimeType, String attachmentName,
                             Boolean hasAttachment, LocalDateTime createdAt, String attachmentUrl) {}
}
