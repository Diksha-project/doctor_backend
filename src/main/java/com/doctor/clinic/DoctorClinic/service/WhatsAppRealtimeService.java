package com.doctor.clinic.DoctorClinic.service;

import java.util.Map;

public interface WhatsAppRealtimeService {
    void publishMessageEvent(Long organizationId, Map<String, Object> payload);
    void publishConversationEvent(Long organizationId, Map<String, Object> payload);
}
