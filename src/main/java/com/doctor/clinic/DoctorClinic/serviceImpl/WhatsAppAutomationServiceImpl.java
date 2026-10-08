package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.AIServices.GeminiServiceLatest;
import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppConversation;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppResponseRule;
import com.doctor.clinic.DoctorClinic.model.ConversationStatus;
import com.doctor.clinic.DoctorClinic.model.Intent;
import com.doctor.clinic.DoctorClinic.model.ResponseMode;
import com.doctor.clinic.DoctorClinic.model.ResponseRuleTriggerType;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppConversationRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppResponseRuleRepo;
import com.doctor.clinic.DoctorClinic.service.WhatsAppAutomationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WhatsAppAutomationServiceImpl implements WhatsAppAutomationService {

    private final DoctorRepo doctorRepo;
    private final PatientRepo patientRepo;
    private final WhatsAppConversationRepo conversationRepo;
    private final WhatsAppResponseRuleRepo responseRuleRepo;
    private final WhatsappServiceImpl whatsappService;
    private final GeminiServiceLatest geminiServiceLatest;
    private final IntentDetector intentDetector;
    private final AppointmentBookingChatService appointmentBookingChatService;
    private final WhatsAppConversationServiceImpl conversationService;
    private final BookingRequestService bookingRequestService;

    public WhatsAppAutomationServiceImpl(DoctorRepo doctorRepo,
                                        PatientRepo patientRepo,
                                        WhatsAppConversationRepo conversationRepo,
                                        WhatsAppResponseRuleRepo responseRuleRepo,
                                        WhatsappServiceImpl whatsappService,
                                        GeminiServiceLatest geminiServiceLatest,
                                        IntentDetector intentDetector,
                                        AppointmentBookingChatService appointmentBookingChatService,
                                        WhatsAppConversationServiceImpl conversationService,
                                        BookingRequestService bookingRequestService) {
        this.doctorRepo = doctorRepo;
        this.patientRepo = patientRepo;
        this.conversationRepo = conversationRepo;
        this.responseRuleRepo = responseRuleRepo;
        this.whatsappService = whatsappService;
        this.geminiServiceLatest = geminiServiceLatest;
        this.intentDetector = intentDetector;
        this.appointmentBookingChatService = appointmentBookingChatService;
        this.conversationService = conversationService;
        this.bookingRequestService = bookingRequestService;
    }

    @Override
    @Async("whatsappTaskExecutor")
    public void processInboundAsync(Long organizationId, Long doctorId, Long patientId, String phoneNumber,
                                   String messageText, String messageType, String mediaId, String mimeType, String attachmentName,
                                   String selectionId) {
        try {
            Doctor doctor = doctorRepo.findById(doctorId).orElse(null);
            if (doctor == null || !doctor.getOrganization().getId().equals(organizationId)) {
                log.warn("Skipping WhatsApp automation for unknown doctor {} in org {}", doctorId, organizationId);
                return;
            }

            Patient patient = patientRepo.findById(patientId).orElse(null);
            if (patient == null || !patient.getOrganization().getId().equals(organizationId)) {
                log.warn("Skipping WhatsApp automation for unknown patient {} in org {}", patientId, organizationId);
                return;
            }

            WhatsAppConversation conversation = conversationRepo
                    .findByOrganizationIdAndPatientIdAndPhoneNumber(organizationId, patient.getId(), phoneNumber)
                    .orElseGet(() -> conversationService.upsertConversation(organizationId, patient, phoneNumber, doctor));

            if (conversation.getStatus() == ConversationStatus.CLOSED
                    || !conversation.isAiEnabled() || conversation.isHumanTakeover()) {
                log.info("Automation disabled for conversation {} with status {}", conversation.getId(), conversation.getStatus());
                return;
            }

            Map<String, Object> interactive = new HashMap<>();
            String response = resolveResponse(doctor, patient, conversation, messageText, messageType, mediaId, mimeType, attachmentName,
                    selectionId, interactive);
            if (response == null || response.isBlank()) {
                log.info("No outbound response generated for conversation {}", conversation.getId());
                return;
            }

            boolean sent = conversationService.sendAutomatedMessage(
                    organizationId, patient, doctor, phoneNumber, response, interactive.isEmpty() ? null : interactive);
            if (sent) {
                log.info("Outbound WhatsApp response sent for conversation {}", conversation.getId());
            } else {
                log.warn("Failed to send outbound WhatsApp response for conversation {}", conversation.getId());
            }
        } catch (Exception ex) {
            log.error("WhatsApp automation processing failed for orgId={} doctorId={} patientId={} phoneNumber={}",
                    organizationId, doctorId, patientId, phoneNumber, ex);
        }
    }

    private String resolveResponse(Doctor doctor, Patient patient, WhatsAppConversation conversation, String messageText,
                                  String messageType, String mediaId, String mimeType, String attachmentName,
                                  String selectionId, Map<String, Object> interactiveOut) {
        String normalized = normalize(messageText);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        if (selectionId != null && selectionId.startsWith("offer_")) {
            String[] parts = selectionId.split("_");
            if (parts.length == 3) {
                try {
                    return bookingRequestService.confirmOffer(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
                } catch (NumberFormatException ignored) {
                    // fall through to normal handling below
                }
            }
        }

        WhatsAppResponseRule rule = findMatchingRule(doctor, conversation.getOrganization(), normalized);
        if (rule != null && rule.getResponseMode() == ResponseMode.TEXT) {
            log.info("Matched response rule {} for conversation {}", rule.getName(), conversation.getId());
            return rule.getResponseText();
        }

        if (rule != null && rule.getResponseMode() == ResponseMode.ESCALATE) {
            return "Thanks for reaching out. Our team has been notified and will get back to you shortly.";
        }

        Optional<AppointmentBookingChatService.Reply> bookingReply = appointmentBookingChatService.handle(doctor, patient, normalized, selectionId);
        if (bookingReply.isPresent()) {
            if (bookingReply.get().interactive() != null) {
                interactiveOut.putAll(bookingReply.get().interactive());
            }
            return bookingReply.get().text();
        }

        String keywordResponse = resolveKeywordResponse(doctor, normalized);
        if (keywordResponse != null) {
            return keywordResponse;
        }

        Intent intent = intentDetector.detect(normalized);
        if (intent == Intent.GREETING || intent == Intent.THANKS || intent == Intent.GOODBYE) {
            return switch (intent) {
                case GREETING -> "Hello! I am " + doctor.getFullName() + "'s virtual assistant. How can I help you with the clinic?";
                case THANKS -> "You’re welcome! Is there anything else I can help you with?";
                case GOODBYE -> "Thank you for contacting the clinic. Have a great day!";
                default -> null;
            };
        }

        String recentHistory = "";
        return geminiServiceLatest.generateResponse(normalized, doctor, recentHistory, null, null);
    }

    private WhatsAppResponseRule findMatchingRule(Doctor doctor, Organization organization, String normalized) {
        List<WhatsAppResponseRule> rules = responseRuleRepo.findByOrganizationIdAndActiveTrueOrderByPriorityDesc(organization.getId());
        if (doctor != null) {
            rules.addAll(responseRuleRepo.findByOrganizationIdAndDoctorIdAndActiveTrueOrderByPriorityDesc(organization.getId(), doctor.getId()));
        }
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (WhatsAppResponseRule rule : rules) {
            if (rule.getTriggerType() == ResponseRuleTriggerType.KEYWORD) {
                if (normalized.contains(rule.getTriggerValue().toLowerCase(Locale.ROOT))) {
                    return rule;
                }
            }
            if (rule.getTriggerType() == ResponseRuleTriggerType.PHRASE) {
                if (normalized.contains(rule.getTriggerValue().toLowerCase(Locale.ROOT))) {
                    return rule;
                }
            }
            if (rule.getTriggerType() == ResponseRuleTriggerType.INTENT) {
                Intent intent = intentDetector.detect(normalized);
                if (intent.name().equalsIgnoreCase(rule.getTriggerValue())) {
                    return rule;
                }
            }
        }
        return null;
    }

    private String resolveKeywordResponse(Doctor doctor, String normalized) {
        if (normalized.contains("consultation fee") || normalized.contains("fee") || normalized.contains("price")) {
            if (doctor.getConsultationFee() != null) {
                return "Consultation fee is ₹" + doctor.getConsultationFee() + ".";
            }
            return "Please contact the clinic for the latest consultation fee details.";
        }

        if (normalized.contains("timing") || normalized.contains("hours") || normalized.contains("open") || normalized.contains("clinic timing")) {
            if (doctor.getConsultationHours() != null && !doctor.getConsultationHours().isBlank()) {
                return "Clinic consultation hours are: " + doctor.getConsultationHours() + ".";
            }
            return "Our clinic is available during standard consultation hours. Please contact the clinic for exact timings.";
        }

        if (normalized.contains("doctor") || normalized.contains("specialist") || normalized.contains("doctor name")) {
            return "You are speaking with Dr. " + doctor.getFullName() + ", a " + (doctor.getSpecialization() == null ? "consultant" : doctor.getSpecialization()) + ".";
        }

        if (normalized.contains("book appointment") || normalized.contains("appointment") || normalized.contains("schedule")) {
            return "Please share your preferred date and time, and I will help check availability for your appointment.";
        }

        return null;
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().toLowerCase(Locale.ROOT);
    }
}
