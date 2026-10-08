package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.time.LocalTime;

import lombok.Builder;
import lombok.Data;

/** One row in the doctor's "Booked Appointments" table. */
@Data
@Builder
public class AppointmentListItemResponse {
    private Long appointmentId;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private LocalTime endTime;
    private Integer durationMinutes;
    private String patientName;
    private String patientPhone;
    private String appointmentType;
    private String appointmentStatus;
    private String bookedVia;
    private String notes;
}
