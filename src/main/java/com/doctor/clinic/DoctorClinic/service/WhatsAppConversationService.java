package com.doctor.clinic.DoctorClinic.service;

import java.util.List;
import java.util.Map;

public interface WhatsAppConversationService {
    List<Map<String, Object>> getConversationsForCurrentOrganization();
    Map<String, Object> getConversation(Long conversationId, Long organizationId);
    List<Map<String, Object>> getMessagesForConversation(Long conversationId, Long organizationId);
    Map<String, Object> sendMessage(Long conversationId, Long organizationId, String content, String messageType);
    Map<String, Object> toggleTakeover(Long conversationId, Long organizationId, boolean takeoverEnabled);
    Map<String, Object> markConversationRead(Long conversationId, Long organizationId);
    Map<String, Object> closeConversation(Long conversationId, Long organizationId);

    Map<String, Object> startConversation(Long organizationId, Long patientId, Long doctorId);

    Map<String, Object> sendAttachment(Long conversationId, Long organizationId,
                                       org.springframework.web.multipart.MultipartFile file, String caption);
}
