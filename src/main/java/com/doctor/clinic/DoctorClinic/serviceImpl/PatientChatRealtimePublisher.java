package com.doctor.clinic.DoctorClinic.serviceImpl;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class PatientChatRealtimePublisher {
    private final SimpMessagingTemplate messagingTemplate;

    public PatientChatRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void publishAfterCommit(ChatMessageCommittedEvent event) {
        var message = event.message();
        String base = "/topic/org/" + message.organizationId() + "/patients";
        messagingTemplate.convertAndSend(base, message);
        messagingTemplate.convertAndSend(base + "/" + message.patientId(), message);
    }
}
