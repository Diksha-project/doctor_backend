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
    private final AppointmentOverflowService appointmentOverflowService;
    private final ClinicMessageScopeDetector clinicMessageScopeDetector;
    private final CloudflareR2StorageService r2StorageService;
    private final PatientChatHistoryService patientChatHistoryService;

    public WhatsAppAutomationServiceImpl(DoctorRepo doctorRepo,
                                        PatientRepo patientRepo,
                                        WhatsAppConversationRepo conversationRepo,
                                        WhatsAppResponseRuleRepo responseRuleRepo,
                                        WhatsappServiceImpl whatsappService,
                                        GeminiServiceLatest geminiServiceLatest,
                                        IntentDetector intentDetector,
                                        AppointmentBookingChatService appointmentBookingChatService,
                                        WhatsAppConversationServiceImpl conversationService,
                                        BookingRequestService bookingRequestService,
                                        AppointmentOverflowService appointmentOverflowService,
                                        ClinicMessageScopeDetector clinicMessageScopeDetector,
                                        CloudflareR2StorageService r2StorageService,
                                        PatientChatHistoryService patientChatHistoryService) {
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
        this.appointmentOverflowService = appointmentOverflowService;
        this.clinicMessageScopeDetector = clinicMessageScopeDetector;
        this.r2StorageService = r2StorageService;
        this.patientChatHistoryService = patientChatHistoryService;
    }

    @Override
    @Async("whatsappTaskExecutor")
    public void processInboundAsync(Long organizationId, Long doctorId, Long patientId, String whatsappMessageId, String phoneNumber,
                                   String messageText, String messageType, String mediaId, String mimeType, String attachmentName,
                                   String selectionId) {
        try {
            Doctor doctor = doctorRepo.findById(doctorId).orElse(null);
            if (doctor == null || !doctor.getOrganization().getId().equals(organizationId)) {
                log.warn("Skipping WhatsApp automation for unknown doctor {} in org {}", doctorId, organizationId);
                return;
            }

            if (samePhone(doctor.getPhoneNumber(), phoneNumber)) {
                String decision = appointmentOverflowService.handleWhatsAppDecision(doctorId, selectionId, messageText);
                if (decision != null) {
                    whatsappService.sendMessage(doctor, phoneNumber, decision);
                }
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

            if (conversation.getStatus() == ConversationStatus.CLOSED && !conversation.isHumanTakeover()) {
                conversation = conversationService.reopenConversation(conversation.getId(), organizationId);
            }

            boolean automationEnabled = conversation.getStatus() != ConversationStatus.CLOSED
                    && conversation.isAiEnabled() && !conversation.isHumanTakeover();
            boolean reportCandidate = mediaId != null
                    && ("image".equalsIgnoreCase(messageType) || "document".equalsIgnoreCase(messageType));
            if (!automationEnabled && !reportCandidate) {
                log.info("Automation disabled for conversation {} with status {}", conversation.getId(), conversation.getStatus());
                return;
            }

            Map<String, Object> interactive = new HashMap<>();
            String response;
            byte[] reportBytes = null;
            String reportMimeType = null;
            if (mediaId != null && ("image".equalsIgnoreCase(messageType) || "document".equalsIgnoreCase(messageType))) {
                try {
                    WhatsappServiceImpl.DownloadedMedia downloaded = whatsappService.downloadMedia(mediaId, doctor.getWhatsappAccessToken());
                    reportBytes = downloaded.bytes();
                    reportMimeType = downloaded.mimeType();
                    if (!hasSupportedFileSignature(reportBytes, reportMimeType)) {
                        response = automationEnabled ? "I can save medical reports sent as a PDF or a clear JPEG, PNG, or WebP image. This file type or file content isn't supported, so it wasn't saved." : null;
                    } else if (!geminiServiceLatest.isValidMedicalReport(reportBytes, reportMimeType)) {
                        response = automationEnabled ? "I couldn't verify this file as a medical report, so it wasn't saved. Please send a clear image or PDF of a lab result, scan, prescription, discharge summary, or similar medical document." : null;
                    } else {
                        CloudflareR2StorageService.StoredFile stored = r2StorageService.storeMedicalReport(
                                organizationId, patient.getId(), doctor.getId(), reportBytes, reportMimeType, attachmentName);
                        conversationService.attachMedicalReport(organizationId, whatsappMessageId, stored);
                        patientChatHistoryService.attachMedicalReport(whatsappMessageId, stored);
                        if (automationEnabled) {
                            String reportPrompt = messageText == null || messageText.isBlank()
                                    ? "Please review the medical report I attached. Explain the visible report content in simple terms."
                                    : messageText;
                            response = resolveResponse(doctor, patient, conversation, reportPrompt, messageType,
                                    null, reportMimeType, attachmentName, selectionId, interactive, reportBytes, reportMimeType);
                        } else {
                            response = null;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Medical report attachment processing failed for orgId={} patientId={} ({})",
                            organizationId, patientId, e.getClass().getSimpleName());
                    response = automationEnabled ? "I couldn't securely save or review that file right now. It wasn't saved; please try again later or contact the clinic." : null;
                }
            } else {
                response = resolveResponse(doctor, patient, conversation, messageText, messageType, mediaId, mimeType,
                        attachmentName, selectionId, interactive, null, null);
            }
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

    private boolean samePhone(String first, String second) {
        if (first == null || second == null) return false;
        String a = first.replaceAll("\\D", "");
        String b = second.replaceAll("\\D", "");
        if (a.length() == 10) a = "91" + a;
        if (b.length() == 10) b = "91" + b;
        return a.equals(b);
    }

    private String resolveResponse(Doctor doctor, Patient patient, WhatsAppConversation conversation, String messageText,
                                  String messageType, String mediaId, String mimeType, String attachmentName,
                                  String selectionId, Map<String, Object> interactiveOut,
                                  byte[] mediaBytes, String mediaContentType) {
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

        Optional<AppointmentBookingChatService.Reply> bookingReply = appointmentBookingChatService.handle(doctor, patient, normalized, selectionId);
        if (bookingReply.isPresent()) {
            if (bookingReply.get().interactive() != null) {
                interactiveOut.putAll(bookingReply.get().interactive());
            }
            return bookingReply.get().text();
        }

        if (!clinicMessageScopeDetector.isInScope(normalized)) {
            return "I can help with health questions and Dr. " + doctor.getFullName()
                    + "’s clinic services, such as appointments, fees, clinic hours, and patient records. "
                    + "I can’t help with unrelated requests. What clinic-related question can I help with?";
        }

        WhatsAppResponseRule rule = findMatchingRule(doctor, conversation.getOrganization(), normalized);
        if (rule != null && rule.getResponseMode() == ResponseMode.TEXT) {
            log.info("Matched response rule {} for conversation {}", rule.getName(), conversation.getId());
            return rule.getResponseText();
        }

        if (rule != null && rule.getResponseMode() == ResponseMode.ESCALATE) {
            return "Thanks for reaching out. Our team has been notified and will get back to you shortly.";
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
        return geminiServiceLatest.generateResponse(normalized, doctor, recentHistory, mediaBytes, mediaContentType);
    }

    private boolean hasSupportedFileSignature(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length < 4 || mimeType == null) return false;
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> bytes.length >= 5 && bytes[0] == '%'
                    && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
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
