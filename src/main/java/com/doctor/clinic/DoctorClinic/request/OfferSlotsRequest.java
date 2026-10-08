package com.doctor.clinic.DoctorClinic.request;

import java.util.List;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class OfferSlotsRequest {

    @NotEmpty(message = "Select at least one slot to offer")
    private List<Long> slotIds;
}
