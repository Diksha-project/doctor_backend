package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationMessageResponse {
    private String direction; // INBOUND, OUTBOUND
    private String content;
    private LocalDateTime createdAt;
}
