package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public record DoctorPatientDashboardResponse(
        Long doctorId,
        String doctorName,
        Long organizationId,
        LocalDate date,
        List<AppointmentPatient> appointments) {

    public record AppointmentPatient(
            Long appointmentId,
            LocalDate appointmentDate,
            LocalTime appointmentTime,
            LocalTime endTime,
            Integer durationMinutes,
            String status,
            String appointmentType,
            String reasonForVisit,
            String symptoms,
            String notes,
            BigDecimal fee,
            String paymentStatus,
            Patient patient,
            List<PreviousVisit> previousVisits,
            List<RecentMessage> recentMessages) {}

    public record Patient(Long id, String name, String phone, String email, Integer age, String gender) {}

    public record PreviousVisit(Long appointmentId, LocalDate date, LocalTime time, String status,
                                String reasonForVisit, String symptoms, String notes) {}

    public record RecentMessage(Long id, String direction, String type, String text,
                                String attachmentMimeType, String attachmentName,
                                String attachmentUrl, LocalDateTime createdAt) {}
}
