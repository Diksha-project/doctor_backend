package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.Map;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.service.WhatsAppRealtimeService;

@Service
public class WhatsAppRealtimeServiceImpl implements WhatsAppRealtimeService {

    private final SimpMessagingTemplate messagingTemplate;

    public WhatsAppRealtimeServiceImpl(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public void publishMessageEvent(Long organizationId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/organizations/" + organizationId + "/whatsapp/messages", payload);
    }

    @Override
    public void publishConversationEvent(Long organizationId, Map<String, Object> payload) {
        messagingTemplate.convertAndSend("/topic/organizations/" + organizationId + "/whatsapp/conversations", payload);
    }
}
