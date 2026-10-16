package com.doctor.clinic.DoctorClinic.service;

public interface WhatsAppAutomationService {
    void processInboundAsync(Long organizationId, Long doctorId, Long patientId, String whatsappMessageId, String phoneNumber,
                            String messageText, String messageType, String mediaId, String mimeType, String attachmentName,
                            String selectionId);
}
