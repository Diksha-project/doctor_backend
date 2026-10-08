package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.ApplicationEventPublisher;

import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.PatientChatMessage;
import com.doctor.clinic.DoctorClinic.repo.PatientChatMessageRepo;
import com.doctor.clinic.DoctorClinic.response.PatientChatRealtimeMessage;

@Service
public class PatientChatHistoryService {

    private final PatientChatMessageRepo messageRepo;
    private final ApplicationEventPublisher eventPublisher;

    public PatientChatHistoryService(PatientChatMessageRepo messageRepo, ApplicationEventPublisher eventPublisher) {
        this.messageRepo = messageRepo;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public String recentPromptContext(Long patientId) {
        List<PatientChatMessageRepo.HistoryRow> rows = messageRepo
                .findByPatientIdOrderByCreatedAtDesc(patientId, PageRequest.of(0, 12));
        Collections.reverse(rows);
        return rows.stream()
                .map(row -> ("OUTBOUND".equals(row.getDirection()) ? "Assistant: " : "Patient: ")
                        + (row.getMessageText() == null ? "[" + row.getMessageType() + " message]" : row.getMessageText()))
                .collect(Collectors.joining("\n"));
    }

    @Transactional
    public void recordInbound(Patient patient, Doctor doctor, String messageId, String type,
                              String text, String mimeType, String fileName, byte[] attachment) {
        PatientChatMessage message = new PatientChatMessage();
        message.setPatient(patient);
        message.setDoctor(doctor);
        message.setDirection("INBOUND");
        message.setWhatsappMessageId(messageId);
        message.setMessageType(type == null ? "unknown" : type);
        message.setMessageText(text);
        message.setAttachmentMimeType(mimeType);
        message.setAttachmentName(fileName);
        message.setAttachmentData(attachment);
        publish(messageRepo.save(message));
    }

    @Transactional
    public void recordOutbound(Patient patient, Doctor doctor, String replyToMessageId, String text) {
        PatientChatMessage message = new PatientChatMessage();
        message.setPatient(patient);
        message.setDoctor(doctor);
        message.setDirection("OUTBOUND");
        message.setReplyToMessageId(replyToMessageId);
        message.setMessageType("text");
        message.setMessageText(text);
        publish(messageRepo.save(message));
    }

    private void publish(PatientChatMessage message) {
        Patient patient = message.getPatient();
        Doctor doctor = message.getDoctor();
        eventPublisher.publishEvent(new ChatMessageCommittedEvent(new PatientChatRealtimeMessage(
                message.getId(), patient.getId(), doctor.getId(), patient.getOrganization().getId(),
                message.getDirection(), message.getMessageType(), message.getMessageText(),
                message.getAttachmentMimeType(), message.getAttachmentName(),
                message.getAttachmentData() != null, message.getCreatedAt())));
    }
}
