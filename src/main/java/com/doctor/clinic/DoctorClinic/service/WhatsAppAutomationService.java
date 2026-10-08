package com.doctor.clinic.DoctorClinic.service;

public interface WhatsAppAutomationService {
    void processInboundAsync(Long organizationId, Long doctorId, Long patientId, String phoneNumber,
                            String messageText, String messageType, String mediaId, String mimeType, String attachmentName,
                            String selectionId);
}
