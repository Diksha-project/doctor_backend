package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDateTime;

public record PatientChatRealtimeMessage(
        Long messageId,
        Long patientId,
        Long doctorId,
        Long organizationId,
        String direction,
        String messageType,
        String messageText,
        String attachmentMimeType,
        String attachmentName,
        boolean hasAttachment,
        LocalDateTime createdAt) {}
