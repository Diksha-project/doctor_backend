package com.doctor.clinic.DoctorClinic.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/** The doctor's "Booked Appointments" tab: a filtered page plus month summary counts. */
@Data
@Builder
public class AppointmentListResponse {
    private List<AppointmentListItemResponse> items;
    private long totalBooked;
    private long completed;
    private long upcoming;
    private long cancelled;
}
