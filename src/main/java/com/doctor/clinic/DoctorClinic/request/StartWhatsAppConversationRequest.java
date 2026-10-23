package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class StartWhatsAppConversationRequest {
    @NotNull
    private Long patientId;

    @NotNull
    private Long userId;
}
