package com.doctor.clinic.DoctorClinic.request;

import com.doctor.clinic.DoctorClinic.model.BookingRequestStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class UpdateBookingRequestStatusRequest {

    @NotNull(message = "status is required")
    private BookingRequestStatus status;

    private String note;
}
