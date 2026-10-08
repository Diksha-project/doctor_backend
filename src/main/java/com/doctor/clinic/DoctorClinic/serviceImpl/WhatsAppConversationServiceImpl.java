package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.PatientChatMessage;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppConversation;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppMessage;
import com.doctor.clinic.DoctorClinic.model.AutomationMode;
import com.doctor.clinic.DoctorClinic.model.ConversationStatus;
import com.doctor.clinic.DoctorClinic.model.MessageDirection;
import com.doctor.clinic.DoctorClinic.model.MessageSenderType;
import com.doctor.clinic.DoctorClinic.model.MessageStatus;
import com.doctor.clinic.DoctorClinic.model.MessageType;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppConversationRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppMessageRepo;
import com.doctor.clinic.DoctorClinic.service.WhatsAppConversationService;
import com.doctor.clinic.DoctorClinic.service.WhatsAppRealtimeService;

@Service
public class WhatsAppConversationServiceImpl implements WhatsAppConversationService {

    private final WhatsAppConversationRepo conversationRepo;
    private final WhatsAppMessageRepo messageRepo;
    private final DoctorRepo doctorRepo;
    private final PatientRepo patientRepo;
    private final WhatsappServiceImpl whatsappService;
    private final WhatsAppRealtimeService realtimeService;
    private final PatientChatHistoryService patientChatHistoryService;

    public WhatsAppConversationServiceImpl(WhatsAppConversationRepo conversationRepo,
                                          WhatsAppMessageRepo messageRepo,
                                          DoctorRepo doctorRepo,
                                          PatientRepo patientRepo,
                                          WhatsappServiceImpl whatsappService,
                                          WhatsAppRealtimeService realtimeService,
                                          PatientChatHistoryService patientChatHistoryService) {
        this.conversationRepo = conversationRepo;
        this.messageRepo = messageRepo;
        this.doctorRepo = doctorRepo;
        this.patientRepo = patientRepo;
        this.whatsappService = whatsappService;
        this.realtimeService = realtimeService;
        this.patientChatHistoryService = patientChatHistoryService;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversationsForCurrentOrganization() {
        Long organizationId = currentOrganizationId();
        List<WhatsAppConversation> conversations = conversationRepo
                .findByOrganizationIdOrderByLastMessageAtDescCreatedAtDesc(organizationId, PageRequest.of(0, 200));
        List<Map<String, Object>> result = new ArrayList<>();
        for (WhatsAppConversation conversation : conversations) {
            result.add(toConversationResponse(conversation));
        }
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getConversation(Long conversationId, Long organizationId) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        return toConversationResponse(conversation);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getMessagesForConversation(Long conversationId, Long organizationId) {
        conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        List<WhatsAppMessage> messages = messageRepo.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(organizationId, conversationId);
        List<Map<String, Object>> result = new ArrayList<>();
        for (WhatsAppMessage message : messages) {
            Map<String, Object> map = new HashMap<>();
            map.put("id", message.getId());
            map.put("conversationId", message.getConversation().getId());
            map.put("direction", message.getDirection().name());
            map.put("senderType", message.getSenderType().name());
            map.put("messageType", message.getMessageType().name());
            map.put("content", message.getContent());
            map.put("status", message.getStatus().name());
            map.put("aiGenerated", message.isAiGenerated());
            map.put("manual", message.isManual());
            map.put("createdAt", message.getCreatedAt());
            map.put("sentAt", message.getSentAt());
            map.put("deliveredAt", message.getDeliveredAt());
            map.put("readAt", message.getReadAt());
            map.put("failedAt", message.getFailedAt());
            result.add(map);
        }
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> sendMessage(Long conversationId, Long organizationId, String content, String messageType) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));

        if (conversation.getDoctor() == null) {
            List<Doctor> doctors = doctorRepo.findByOrganizationId(organizationId);
            if (doctors.isEmpty()) {
                throw new IllegalStateException("No doctor is available for this organization");
            }
            conversation.setDoctor(doctors.get(0));
            conversationRepo.save(conversation);
        }

        String trimmed = content == null ? "" : content.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("Message content is required");
        }

        MessageType type = messageType == null ? MessageType.TEXT : MessageType.valueOf(messageType.toUpperCase());
        WhatsAppMessage message = sendPersistedOutbound(conversation, trimmed, type, false, null,
                conversation.getDoctor());

        Map<String, Object> result = new HashMap<>();
        result.put("messageId", message.getId());
        result.put("status", message.getStatus().name());
        result.put("sent", message.getStatus() == MessageStatus.SENT);
        return result;
    }

    @Transactional
    public boolean sendAutomatedMessage(Long organizationId, Patient patient, Doctor doctor,
                                        String phoneNumber, String content) {
        return sendAutomatedMessage(organizationId, patient, doctor, phoneNumber, content, null);
    }

    /** Sends an AI reply; when {@code interactive} is set it is sent as a button/list message, falling back to plain text. */
    public boolean sendAutomatedMessage(Long organizationId, Patient patient, Doctor doctor,
                                        String phoneNumber, String content, Map<String, Object> interactive) {
        WhatsAppConversation conversation = findOrCreateConversation(organizationId, patient, phoneNumber, doctor);
        if (conversation.getStatus() == ConversationStatus.CLOSED
                || !conversation.isAiEnabled() || conversation.isHumanTakeover()) {
            return false;
        }
        WhatsAppMessage message = sendPersistedOutbound(
                conversation, content, MessageType.TEXT, true, interactive, doctor);
        return message.getStatus() == MessageStatus.SENT;
    }

    private WhatsAppMessage sendPersistedOutbound(WhatsAppConversation conversation, String content,
                                                   MessageType type, boolean aiGenerated,
                                                   Map<String, Object> interactive, Doctor deliveryDoctor) {
        WhatsAppMessage message = new WhatsAppMessage();
        message.setConversation(conversation);
        message.setOrganization(conversation.getOrganization());
        message.setPatient(conversation.getPatient());
        message.setDoctor(conversation.getDoctor());
        message.setDirection(MessageDirection.OUTBOUND);
        message.setSenderType(aiGenerated ? MessageSenderType.AI : MessageSenderType.ADMIN);
        message.setMessageType(type);
        message.setContent(content);
        message.setStatus(MessageStatus.PROCESSING);
        message.setAiGenerated(aiGenerated);
        message.setManual(!aiGenerated);
        message = messageRepo.saveAndFlush(message);
        publishMessageEvent(message, "MESSAGE_CREATED");

        WhatsappServiceImpl.SendResult sendResult = null;
        if (interactive != null) {
            sendResult = whatsappService.sendInteractiveWithResult(
                    deliveryDoctor, conversation.getPhoneNumber(), interactive);
        }
        if (sendResult == null || !sendResult.sent()) {
            sendResult = whatsappService.sendMessageWithResult(
                    deliveryDoctor, conversation.getPhoneNumber(), content);
        }
        LocalDateTime now = LocalDateTime.now();
        if (sendResult.sent()) {
            message.setStatus(MessageStatus.SENT);
            message.setSentAt(now);
            message.setMetaMessageId(sendResult.providerMessageId());
            patientChatHistoryService.recordOutbound(
                    conversation.getPatient(), deliveryDoctor, null, content);
        } else {
            message.setStatus(MessageStatus.FAILED);
            message.setFailedAt(now);
            message.setErrorMessage("Meta WhatsApp API rejected the outbound message");
        }
        message = messageRepo.saveAndFlush(message);

        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationRepo.save(conversation);
        publishMessageEvent(message, "MESSAGE_STATUS_UPDATED");
        publishConversationUpdated(conversation);
        return message;
    }

    private void publishMessageEvent(WhatsAppMessage message, String eventType) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", eventType);
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversation().getId());
        event.put("organizationId", message.getOrganization().getId());
        event.put("patientId", message.getPatient() == null ? null : message.getPatient().getId());
        event.put("direction", message.getDirection().name());
        event.put("senderType", message.getSenderType().name());
        event.put("messageType", message.getMessageType().name());
        event.put("content", message.getContent());
        event.put("status", message.getStatus().name());
        event.put("aiGenerated", message.isAiGenerated());
        event.put("manual", message.isManual());
        event.put("timestamp", LocalDateTime.now());
        realtimeService.publishMessageEvent(message.getOrganization().getId(), event);
    }

    private void publishConversationUpdated(WhatsAppConversation conversation) {
        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONVERSATION_UPDATED");
        event.put("conversationId", conversation.getId());
        event.put("organizationId", conversation.getOrganization().getId());
        event.put("lastMessageAt", conversation.getLastMessageAt());
        event.put("status", conversation.getStatus().name());
        event.put("automationMode", conversation.getAutomationMode().name());
        event.put("unreadCount", unreadCount(conversation));
        realtimeService.publishConversationEvent(conversation.getOrganization().getId(), event);
    }

    @Override
    @Transactional
    public Map<String, Object> startConversation(Long organizationId, Long patientId, Long doctorId) {
        Patient patient = patientRepo.findByIdAndOrganizationId(patientId, organizationId)
                .orElseThrow(() -> BusinessException.notFound("Patient", patientId));
        Doctor doctor = doctorRepo.findByIdAndOrganizationId(doctorId, organizationId)
                .orElseThrow(() -> BusinessException.notFound("Doctor", doctorId));
        String phone = patient.getNormalizedPhone();
        if (phone == null || phone.isBlank()) {
            throw new IllegalArgumentException("Patient has no phone number");
        }
        if (phone.matches("\\d{10}")) {
            phone = "91" + phone;
        }
        WhatsAppConversation conversation = findOrCreateConversation(organizationId, patient, phone, doctor);
        if (conversation.getStatus() == ConversationStatus.CLOSED) {
            conversation.setStatus(ConversationStatus.AI_ACTIVE);
            conversation.setAiEnabled(true);
            conversation = conversationRepo.save(conversation);
        }
        return toConversationResponse(conversation);
    }

    @Override
    @Transactional
    public Map<String, Object> sendAttachment(Long conversationId, Long organizationId,
                                              org.springframework.web.multipart.MultipartFile file, String caption) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        Doctor doctor = conversation.getDoctor();
        if (doctor == null) {
            throw new IllegalStateException("No doctor is linked to this conversation");
        }
        if (file == null || file.isEmpty() || file.getSize() > 14_000_000) {
            throw new IllegalArgumentException("Attachment is empty or larger than 14 MB");
        }
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        String filename = file.getOriginalFilename() == null ? "attachment" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (java.io.IOException e) {
            throw new IllegalArgumentException("Unable to read attachment");
        }

        boolean image = mime.startsWith("image/");
        WhatsAppMessage message = new WhatsAppMessage();
        message.setConversation(conversation);
        message.setOrganization(conversation.getOrganization());
        message.setPatient(conversation.getPatient());
        message.setDoctor(doctor);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setSenderType(MessageSenderType.ADMIN);
        message.setMessageType(image ? MessageType.IMAGE : MessageType.DOCUMENT);
        String label = (image ? "Image: " : "Document: ") + filename;
        message.setContent(caption == null || caption.isBlank() ? label : label + "\n" + caption.trim());
        message.setStatus(MessageStatus.PROCESSING);
        message.setManual(true);
        message = messageRepo.saveAndFlush(message);

        WhatsappServiceImpl.SendResult result = whatsappService.sendMediaWithResult(
                doctor, conversation.getPhoneNumber(), bytes, mime, filename, caption);
        LocalDateTime now = LocalDateTime.now();
        if (result.sent()) {
            message.setStatus(MessageStatus.SENT);
            message.setSentAt(now);
            message.setMetaMessageId(result.providerMessageId());
        } else {
            message.setStatus(MessageStatus.FAILED);
            message.setFailedAt(now);
            message.setErrorMessage("Meta WhatsApp API rejected the attachment");
        }
        message = messageRepo.saveAndFlush(message);
        conversation.setLastMessageAt(now);
        conversation.setUpdatedAt(now);
        conversationRepo.save(conversation);
        publishMessageEvent(message, "MESSAGE_STATUS_UPDATED");
        publishConversationUpdated(conversation);

        Map<String, Object> response = new HashMap<>();
        response.put("messageId", message.getId());
        response.put("status", message.getStatus().name());
        response.put("sent", message.getStatus() == MessageStatus.SENT);
        return response;
    }

    private Map<String, Object> toConversationResponse(WhatsAppConversation conversation) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", conversation.getId());
        map.put("patientId", conversation.getPatient() == null ? null : conversation.getPatient().getId());
        map.put("patientName", conversation.getPatient() == null ? null : conversation.getPatient().getFullName());
        if (conversation.getPatient() != null) {
            int year = conversation.getPatient().getCreatedAt() == null
                    ? LocalDateTime.now().getYear() : conversation.getPatient().getCreatedAt().getYear();
            map.put("patientCode", String.format("PT-%d-%04d", year, conversation.getPatient().getId()));
        }
        map.put("doctorId", conversation.getDoctor() == null ? null : conversation.getDoctor().getId());
        map.put("phoneNumber", conversation.getPhoneNumber());
        map.put("status", conversation.getStatus().name());
        map.put("automationMode", conversation.getAutomationMode().name());
        map.put("humanTakeover", conversation.isHumanTakeover());
        map.put("aiEnabled", conversation.isAiEnabled());
        map.put("lastMessageAt", conversation.getLastMessageAt());
        map.put("unreadCount", unreadCount(conversation));
        map.put("createdAt", conversation.getCreatedAt());
        map.put("updatedAt", conversation.getUpdatedAt());

        List<WhatsAppMessage> latest = messageRepo.findByOrganizationIdAndConversationIdOrderByCreatedAtDesc(
                conversation.getOrganization().getId(), conversation.getId(), PageRequest.of(0, 1));
        map.put("lastMessagePreview", latest.isEmpty() ? null : latest.get(0).getContent());
        if (!latest.isEmpty()) {
            map.put("lastMessageDirection", latest.get(0).getDirection().name());
        }
        return map;
    }

    private long unreadCount(WhatsAppConversation conversation) {
        return messageRepo.countByOrganizationIdAndConversationIdAndDirectionAndStatusAndCreatedAtGreaterThanEqual(
                conversation.getOrganization().getId(),
                conversation.getId(),
                MessageDirection.INBOUND,
                MessageStatus.RECEIVED,
                conversation.getCreatedAt());
    }

    @Override
    @Transactional
    public Map<String, Object> toggleTakeover(Long conversationId, Long organizationId, boolean takeoverEnabled) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        conversation.setHumanTakeover(takeoverEnabled);
        conversation.setAiEnabled(!takeoverEnabled);
        conversation.setAutomationMode(takeoverEnabled ? AutomationMode.MANUAL : AutomationMode.HYBRID);
        conversation.setStatus(takeoverEnabled ? ConversationStatus.MANUAL : ConversationStatus.AI_ACTIVE);
        conversation = conversationRepo.save(conversation);

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "CONVERSATION_STATUS_UPDATED");
        event.put("conversationId", conversation.getId());
        event.put("organizationId", organizationId);
        event.put("humanTakeover", conversation.isHumanTakeover());
        event.put("aiEnabled", conversation.isAiEnabled());
        event.put("status", conversation.getStatus().name());
        event.put("automationMode", conversation.getAutomationMode().name());
        realtimeService.publishConversationEvent(organizationId, event);

        Map<String, Object> result = new HashMap<>();
        result.put("conversationId", conversation.getId());
        result.put("humanTakeover", conversation.isHumanTakeover());
        result.put("aiEnabled", conversation.isAiEnabled());
        result.put("status", conversation.getStatus().name());
        return result;
    }

    @Override
    @Transactional
    public Map<String, Object> markConversationRead(Long conversationId, Long organizationId) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        int markedRead = messageRepo.markInboundMessagesRead(
                organizationId, conversationId, MessageDirection.INBOUND,
                MessageStatus.RECEIVED, MessageStatus.READ, LocalDateTime.now());
        if (markedRead > 0) {
            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CONVERSATION_UPDATED");
            event.put("conversationId", conversation.getId());
            event.put("organizationId", organizationId);
            event.put("unreadCount", 0);
            event.put("status", conversation.getStatus().name());
            event.put("automationMode", conversation.getAutomationMode().name());
            realtimeService.publishConversationEvent(organizationId, event);
        }
        return toConversationResponse(conversation);
    }

    @Override
    @Transactional
    public Map<String, Object> closeConversation(Long conversationId, Long organizationId) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        boolean changed = conversation.getStatus() != ConversationStatus.CLOSED || conversation.isAiEnabled();
        if (changed) {
            conversation.setStatus(ConversationStatus.CLOSED);
            conversation.setAiEnabled(false);
            conversation = conversationRepo.save(conversation);

            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CONVERSATION_STATUS_UPDATED");
            event.put("conversationId", conversation.getId());
            event.put("organizationId", organizationId);
            event.put("status", conversation.getStatus().name());
            event.put("automationMode", conversation.getAutomationMode().name());
            event.put("aiEnabled", conversation.isAiEnabled());
            event.put("humanTakeover", conversation.isHumanTakeover());
            event.put("unreadCount", unreadCount(conversation));
            realtimeService.publishConversationEvent(organizationId, event);
        }
        return toConversationResponse(conversation);
    }

    @Transactional
    public WhatsAppConversation reopenConversation(Long conversationId, Long organizationId) {
        WhatsAppConversation conversation = conversationRepo.findByOrganizationIdAndId(organizationId, conversationId)
                .orElseThrow(() -> BusinessException.notFound("WhatsApp conversation", conversationId));
        if (conversation.getStatus() == ConversationStatus.CLOSED && !conversation.isHumanTakeover()) {
            conversation.setStatus(ConversationStatus.AI_ACTIVE);
            conversation.setAiEnabled(true);
            conversation = conversationRepo.save(conversation);

            Map<String, Object> event = new HashMap<>();
            event.put("eventType", "CONVERSATION_STATUS_UPDATED");
            event.put("conversationId", conversation.getId());
            event.put("organizationId", organizationId);
            event.put("status", conversation.getStatus().name());
            event.put("automationMode", conversation.getAutomationMode().name());
            event.put("aiEnabled", conversation.isAiEnabled());
            event.put("humanTakeover", conversation.isHumanTakeover());
            realtimeService.publishConversationEvent(organizationId, event);
        }
        return conversation;
    }

    private Long currentOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getDetails() == null) {
            throw new IllegalStateException("Organization context is missing");
        }
        Object details = authentication.getDetails();
        if (details instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Organization context is missing");
    }

    private MessageType parseMessageType(String rawType) {
        if (rawType == null || rawType.isBlank()) {
            return MessageType.TEXT;
        }
        String normalized = rawType.trim().toUpperCase();
        try {
            return MessageType.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return MessageType.OTHER;
        }
    }

    public WhatsAppConversation upsertConversation(Long organizationId, com.doctor.clinic.DoctorClinic.entity.Patient patient, String phoneNumber, Doctor doctor) {
        WhatsAppConversation conversation = findOrCreateConversation(organizationId, patient, phoneNumber, doctor);
        conversation.setDoctor(doctor != null ? doctor : conversation.getDoctor());
        conversation.setLastMessageAt(LocalDateTime.now());
        conversation.setUpdatedAt(LocalDateTime.now());
        return conversationRepo.save(conversation);
    }

    private WhatsAppConversation findOrCreateConversation(Long organizationId, Patient patient, String phoneNumber, Doctor doctor) {
        if (patient.getOrganization() == null || !patient.getOrganization().getId().equals(organizationId)
                || doctor == null || doctor.getOrganization() == null
                || !doctor.getOrganization().getId().equals(organizationId)) {
            throw new IllegalArgumentException("WhatsApp patient and doctor must belong to the conversation organization");
        }

        return conversationRepo.findByOrganizationIdAndPatientIdAndPhoneNumber(organizationId, patient.getId(), phoneNumber)
                .orElseGet(() -> {
                    WhatsAppConversation conversation = new WhatsAppConversation();
                    conversation.setOrganization(patient.getOrganization());
                    conversation.setPatient(patient);
                    conversation.setPhoneNumber(phoneNumber);
                    conversation.setDoctor(doctor);
                    conversation.setStatus(ConversationStatus.AI_ACTIVE);
                    conversation.setAutomationMode(AutomationMode.HYBRID);
                    conversation.setAiEnabled(true);
                    conversation.setHumanTakeover(false);
                    conversation = conversationRepo.saveAndFlush(conversation);

                    Map<String, Object> event = new HashMap<>();
                    event.put("eventType", "CONVERSATION_CREATED");
                    event.put("conversationId", conversation.getId());
                    event.put("organizationId", organizationId);
                    event.put("patientId", patient.getId());
                    event.put("doctorId", doctor.getId());
                    event.put("phoneNumber", phoneNumber);
                    event.put("status", conversation.getStatus().name());
                    event.put("automationMode", conversation.getAutomationMode().name());
                    event.put("timestamp", conversation.getCreatedAt());
                    realtimeService.publishConversationEvent(organizationId, event);
                    return conversation;
                });
    }

    @Transactional
    public WhatsAppMessage recordInboundMessage(Long organizationId, Patient patient, Doctor doctor, String phoneNumber,
                                               String metaMessageId, String content, String messageType) {
        WhatsAppConversation conversation = upsertConversation(organizationId, patient, phoneNumber, doctor);

        if (metaMessageId != null && !metaMessageId.isBlank()) {
            WhatsAppMessage existing = messageRepo.findByMetaMessageId(metaMessageId).orElse(null);
            if (existing != null) {
                return existing;
            }
        }

        WhatsAppMessage message = new WhatsAppMessage();
        message.setConversation(conversation);
        message.setOrganization(conversation.getOrganization());
        message.setPatient(patient);
        message.setDoctor(doctor);
        message.setMetaMessageId(metaMessageId);
        message.setDirection(MessageDirection.INBOUND);
        message.setSenderType(MessageSenderType.PATIENT);
        message.setMessageType(parseMessageType(messageType));
        message.setContent(content);
        message.setStatus(MessageStatus.RECEIVED);

        try {
            message = messageRepo.saveAndFlush(message);
        } catch (DataIntegrityViolationException ex) {
            if (metaMessageId != null && !metaMessageId.isBlank()) {
                return messageRepo.findByMetaMessageId(metaMessageId)
                        .orElseThrow(() -> ex);
            }
            throw ex;
        }

        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepo.save(conversation);

        Map<String, Object> messageEvent = new HashMap<>();
        messageEvent.put("eventType", "MESSAGE_CREATED");
        messageEvent.put("messageId", message.getId());
        messageEvent.put("conversationId", conversation.getId());
        messageEvent.put("organizationId", organizationId);
        messageEvent.put("patientId", patient.getId());
        messageEvent.put("direction", message.getDirection().name());
        messageEvent.put("content", message.getContent());
        messageEvent.put("status", message.getStatus().name());
        messageEvent.put("timestamp", LocalDateTime.now());
        realtimeService.publishMessageEvent(organizationId, messageEvent);

        Map<String, Object> conversationEvent = new HashMap<>();
        conversationEvent.put("eventType", "CONVERSATION_UPDATED");
        conversationEvent.put("conversationId", conversation.getId());
        conversationEvent.put("organizationId", organizationId);
        conversationEvent.put("lastMessageAt", conversation.getLastMessageAt());
        conversationEvent.put("phoneNumber", phoneNumber);
        conversationEvent.put("unreadCount", unreadCount(conversation));
        realtimeService.publishConversationEvent(organizationId, conversationEvent);

        return message;
    }

    @Transactional
    public WhatsAppMessage recordOutboundMessage(Long organizationId, Patient patient, Doctor doctor, String phoneNumber,
                                                String content, boolean aiGenerated) {
        WhatsAppConversation conversation = upsertConversation(organizationId, patient, phoneNumber, doctor);
        WhatsAppMessage message = new WhatsAppMessage();
        message.setConversation(conversation);
        message.setOrganization(conversation.getOrganization());
        message.setPatient(patient);
        message.setDoctor(doctor);
        message.setDirection(MessageDirection.OUTBOUND);
        message.setSenderType(aiGenerated ? MessageSenderType.AI : MessageSenderType.ADMIN);
        message.setMessageType(MessageType.TEXT);
        message.setContent(content);
        message.setStatus(MessageStatus.SENT);
        message.setAiGenerated(aiGenerated);
        message.setManual(!aiGenerated);
        message = messageRepo.save(message);
        patientChatHistoryService.recordOutbound(patient, doctor, null, content);

        conversation.setLastMessageAt(LocalDateTime.now());
        conversationRepo.save(conversation);

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "MESSAGE_CREATED");
        event.put("messageId", message.getId());
        event.put("conversationId", conversation.getId());
        event.put("organizationId", organizationId);
        event.put("direction", message.getDirection().name());
        event.put("content", message.getContent());
        event.put("status", message.getStatus().name());
        event.put("timestamp", LocalDateTime.now());
        realtimeService.publishMessageEvent(organizationId, event);
        return message;
    }

    @Transactional
    public boolean recordLegacyMessage(PatientChatMessage legacyMessage) {
        Patient patient = legacyMessage.getPatient();
        Doctor doctor = legacyMessage.getDoctor();
        if (patient == null || doctor == null || patient.getOrganization() == null
                || doctor.getOrganization() == null
                || !patient.getOrganization().getId().equals(doctor.getOrganization().getId())) {
            return false;
        }

        boolean inbound = "INBOUND".equalsIgnoreCase(legacyMessage.getDirection());
        String metaMessageId = inbound && legacyMessage.getWhatsappMessageId() != null
                && !legacyMessage.getWhatsappMessageId().isBlank()
                ? legacyMessage.getWhatsappMessageId()
                : "legacy-patient-chat-" + legacyMessage.getId();
        if (messageRepo.findByMetaMessageId(metaMessageId).isPresent()) {
            return false;
        }

        String normalizedPhone = patient.getNormalizedPhone() == null
                ? "" : patient.getNormalizedPhone().replaceAll("\\D", "");
        String conversationPhone = normalizedPhone.length() == 10
                ? "91" + normalizedPhone : normalizedPhone;
        if (conversationPhone.isBlank()) {
            return false;
        }

        Long organizationId = patient.getOrganization().getId();
        WhatsAppConversation conversation = findOrCreateConversation(
                organizationId, patient, conversationPhone, doctor);

        WhatsAppMessage message = new WhatsAppMessage();
        message.setConversation(conversation);
        message.setOrganization(patient.getOrganization());
        message.setPatient(patient);
        message.setDoctor(doctor);
        message.setMetaMessageId(metaMessageId);
        message.setDirection(inbound ? MessageDirection.INBOUND : MessageDirection.OUTBOUND);
        message.setSenderType(inbound ? MessageSenderType.PATIENT : MessageSenderType.SYSTEM);
        message.setMessageType(parseMessageType(legacyMessage.getMessageType()));
        message.setContent(legacyMessage.getMessageText());
        message.setStatus(inbound ? MessageStatus.READ : MessageStatus.SENT);
        if (inbound) {
            message.setReadAt(legacyMessage.getCreatedAt());
        }
        if (!inbound) {
            message.setSentAt(legacyMessage.getCreatedAt());
        }
        message.setCreatedAt(legacyMessage.getCreatedAt());
        messageRepo.saveAndFlush(message);

        if (conversation.getLastMessageAt() == null
                || (legacyMessage.getCreatedAt() != null && legacyMessage.getCreatedAt().isAfter(conversation.getLastMessageAt()))) {
            conversation.setLastMessageAt(legacyMessage.getCreatedAt());
            conversationRepo.save(conversation);
        }
        return true;
    }

    @Transactional
    public boolean updateDeliveryStatus(String providerMessageId, String providerStatus,
                                        LocalDateTime statusAt, String errorMessage) {
        WhatsAppMessage message = messageRepo.findByMetaMessageId(providerMessageId).orElse(null);
        if (message == null) {
            return false;
        }

        MessageStatus newStatus = switch (providerStatus.toLowerCase()) {
            case "sent" -> MessageStatus.SENT;
            case "delivered" -> MessageStatus.DELIVERED;
            case "read" -> MessageStatus.READ;
            case "failed" -> MessageStatus.FAILED;
            default -> null;
        };
        if (newStatus == null) {
            return false;
        }

        message.setStatus(newStatus);
        switch (newStatus) {
            case SENT -> message.setSentAt(statusAt);
            case DELIVERED -> message.setDeliveredAt(statusAt);
            case READ -> message.setReadAt(statusAt);
            case FAILED -> {
                message.setFailedAt(statusAt);
                message.setErrorMessage(errorMessage);
            }
            default -> { }
        }
        message = messageRepo.saveAndFlush(message);

        Map<String, Object> event = new HashMap<>();
        event.put("eventType", "MESSAGE_STATUS_UPDATED");
        event.put("messageId", message.getId());
        event.put("conversationId", message.getConversation().getId());
        event.put("organizationId", message.getOrganization().getId());
        event.put("status", message.getStatus().name());
        event.put("timestamp", statusAt);
        realtimeService.publishMessageEvent(message.getOrganization().getId(), event);
        return true;
    }
}
