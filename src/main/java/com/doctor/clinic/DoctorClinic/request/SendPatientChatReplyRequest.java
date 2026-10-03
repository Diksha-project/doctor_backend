package com.doctor.clinic.DoctorClinic.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SendPatientChatReplyRequest {
    @NotNull
    private Long doctorId;

    @NotBlank
    @Size(max = 4096)
    private String message;

    private String replyToMessageId;
}
