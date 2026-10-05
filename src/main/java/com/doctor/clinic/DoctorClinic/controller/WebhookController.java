package com.doctor.clinic.DoctorClinic.controller;

import java.util.Optional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.AIServices.GeminiServiceLatest;
import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.model.Intent;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.ProcessedWhatsappMessageRepo;
import com.doctor.clinic.DoctorClinic.serviceImpl.IntentDetector;
import com.doctor.clinic.DoctorClinic.serviceImpl.PatientChatHistoryService;
import com.doctor.clinic.DoctorClinic.serviceImpl.PatientProfileService;
import com.doctor.clinic.DoctorClinic.serviceImpl.AppointmentBookingChatService;
import com.doctor.clinic.DoctorClinic.service.WhatsAppAutomationService;
import com.doctor.clinic.DoctorClinic.serviceImpl.WhatsAppConversationServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    @Value("${facebook.webhook.verify-token}")
    private String verifyToken;

    private final DoctorRepo doctorRepo;
    private final GeminiServiceLatest aiService;
    private final WhatsappServiceImpl whatsAppService;
    private final ObjectMapper objectMapper;
    private final IntentDetector intentDetector;
    private final ProcessedWhatsappMessageRepo processedMessageRepo;
    private final PatientProfileService patientProfileService;
    private final PatientChatHistoryService patientChatHistoryService;
    private final AppointmentBookingChatService appointmentBookingChatService;
    private final WhatsAppConversationServiceImpl whatsappConversationService;
    private final WhatsAppAutomationService whatsappAutomationService;

    public WebhookController(
            DoctorRepo doctorRepo,
            GeminiServiceLatest aiService,
            WhatsappServiceImpl whatsAppService,
            ObjectMapper objectMapper,
            IntentDetector intentDetector,
            ProcessedWhatsappMessageRepo processedMessageRepo,
            PatientProfileService patientProfileService,
            PatientChatHistoryService patientChatHistoryService,
            AppointmentBookingChatService appointmentBookingChatService,
            WhatsAppConversationServiceImpl whatsappConversationService,
            WhatsAppAutomationService whatsappAutomationService) {

        this.doctorRepo = doctorRepo;
        this.aiService = aiService;
        this.whatsAppService = whatsAppService;
        this.objectMapper = objectMapper;
        this.intentDetector = intentDetector;
        this.processedMessageRepo = processedMessageRepo;
        this.patientProfileService = patientProfileService;
        this.patientChatHistoryService = patientChatHistoryService;
        this.appointmentBookingChatService = appointmentBookingChatService;
        this.whatsappConversationService = whatsappConversationService;
        this.whatsappAutomationService = whatsappAutomationService;
    }

    /*
     * ============================================================
     * HEALTH CHECK
     * ============================================================
     *
     * Used to check whether Render application is alive.
     *
     * GET /webhook/health
     */
    @GetMapping("/health")
    public String health() {

        System.out.println("Health check received");

        return "OK";
    }

    /*
     * ============================================================
     * META WEBHOOK VERIFICATION
     * ============================================================
     *
     * Meta calls:
     *
     * GET /webhook/whatsapp
     *
     * Meta sends:
     *
     * hub.mode
     * hub.verify_token
     * hub.challenge
     */
    @GetMapping("/whatsapp")
    public String verifyWebhook(
            @RequestParam("hub.mode") String mode,
            @RequestParam("hub.verify_token") String token,
            @RequestParam("hub.challenge") String challenge) {

        System.out.println("=================================");
        System.out.println("WHATSAPP WEBHOOK VERIFICATION");
        System.out.println("=================================");

        System.out.println("Mode = " + mode);

        System.out.println(
                "Verify Token Received = "
                        + (token != null && !token.isBlank()));

        if ("subscribe".equals(mode)
                && verifyToken.equals(token)) {

            System.out.println(
                    "Webhook verification SUCCESS");

            return challenge;
        }

        System.out.println(
                "Webhook verification FAILED");

        return "Verification failed";
    }

    /*
     * ============================================================
     * WHATSAPP INCOMING MESSAGE
     * ============================================================
     *
     * Meta calls:
     *
     * POST /webhook/whatsapp
     *
     * whenever a WhatsApp event/message is received.
     */
    @PostMapping("/whatsapp")
    public ResponseEntity<String> handleIncomingMessages(@RequestBody String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode entries = root.path("entry");
            if (entries.isArray()) {
                for (JsonNode entry : entries) {
                    JsonNode changes = entry.path("changes");
                    if (!changes.isArray()) continue;
                    for (JsonNode change : changes) {
                        JsonNode value = change.path("value");
                        JsonNode statuses = value.path("statuses");
                        if (statuses.isArray()) {
                            for (JsonNode status : statuses) {
                                try {
                                    processMessageStatus(status);
                                } catch (Exception e) {
                                    System.err.println("Could not process WhatsApp status callback: " + e.getMessage());
                                }
                            }
                        }
                        JsonNode messages = value.path("messages");
                        if (!messages.isArray()) continue; // status callbacks have no messages
                        for (JsonNode message : messages) {
                            try {
                                processIncomingMessage(value, message);
                            } catch (Exception e) {
                                System.err.println("Could not process WhatsApp message: " + e.getMessage());
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("Could not parse WhatsApp webhook: " + e.getMessage());
            e.printStackTrace();
        }
        // Acknowledge Meta callbacks, including status updates, promptly.
        return ResponseEntity.ok("EVENT_RECEIVED");
    }

    private void processIncomingMessage(JsonNode value, JsonNode message) {
        String fromNumber = message.path("from").asText(null);
        if (fromNumber == null || fromNumber.isBlank()) return;

        String messageId = message.path("id").asText(null);
        if (messageId != null && !messageId.isBlank()
                && processedMessageRepo.claimMessageId(messageId) == 0) {
            System.out.println("Ignoring duplicate WhatsApp message ID = " + messageId);
            return;
        }

        String doctorNumber = value.path("metadata").path("display_phone_number").asText(null);
        if (doctorNumber == null || doctorNumber.isBlank()) return;
        doctorNumber = doctorNumber.replaceAll("\\D", "");

        Optional<Doctor> doctorOptional = doctorRepo.findByWhatsappNumber(doctorNumber);
        if (doctorOptional.isEmpty()) {
            System.out.println("No doctor is configured for WhatsApp number " + doctorNumber);
            return;
        }
        Doctor doctor = doctorOptional.get();
        if (!doctor.isWhatsappActivated()
                || doctor.getWhatsappPhoneNumberId() == null || doctor.getWhatsappPhoneNumberId().isBlank()
                || doctor.getWhatsappAccessToken() == null || doctor.getWhatsappAccessToken().isBlank()) {
            System.out.println("WhatsApp is not fully configured for doctor " + doctor.getId());
            return;
        }

        String type = message.path("type").asText("unknown");
        String text = extractMessageText(type, message);
        String contactName = findContactName(value, fromNumber);
        Patient patient = patientProfileService.upsert(doctor.getOrganization(), fromNumber,
                contactName, null, null, null);

        String mediaId = null;
        String mimeType = null;
        String attachmentName = null;
        if (isMediaMessage(type)) {
            JsonNode mediaNode = message.path(type);
            mediaId = nonBlank(mediaNode.path("id").asText(null));
            mimeType = nonBlank(mediaNode.path("mime_type").asText(null));
            attachmentName = nonBlank(mediaNode.path("filename").asText(null));
        }

        whatsappConversationService.recordInboundMessage(
                doctor.getOrganization().getId(),
                patient,
                doctor,
                fromNumber,
                messageId,
                text,
                type);
        patientChatHistoryService.recordInbound(
                patient, doctor, messageId, type, text, mimeType, attachmentName, null);

        whatsappAutomationService.processInboundAsync(
                doctor.getOrganization().getId(),
                doctor.getId(),
                patient.getId(),
                fromNumber,
                text,
                type,
                mediaId,
                mimeType,
                attachmentName);
    }

    private void processMessageStatus(JsonNode statusNode) {
        String providerMessageId = nonBlank(statusNode.path("id").asText(null));
        String providerStatus = nonBlank(statusNode.path("status").asText(null));
        if (providerMessageId == null || providerStatus == null) {
            return;
        }

        long epochSeconds = statusNode.path("timestamp").asLong(0);
        LocalDateTime statusAt = epochSeconds > 0
                ? LocalDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneId.systemDefault())
                : LocalDateTime.now();
        JsonNode firstError = statusNode.path("errors").path(0);
        String error = nonBlank(firstError.path("title").asText(null));
        if (error == null) {
            error = nonBlank(firstError.path("message").asText(null));
        }
        whatsappConversationService.updateDeliveryStatus(providerMessageId, providerStatus, statusAt, error);
    }

    private String findContactName(JsonNode value, String fromNumber) {
        JsonNode contacts = value.path("contacts");
        if (!contacts.isArray()) return null;
        for (JsonNode contact : contacts) {
            if (fromNumber.equals(contact.path("wa_id").asText())) {
                return nonBlank(contact.path("profile").path("name").asText(null));
            }
        }
        return null;
    }

    private String extractMessageText(String type, JsonNode message) {
        return switch (type) {
            case "text" -> nonBlank(message.path("text").path("body").asText(null));
            case "interactive" -> {
                JsonNode interactive = message.path("interactive");
                String buttonTitle = interactive.path("button_reply").path("title").asText(null);
                yield nonBlank(buttonTitle != null ? buttonTitle
                        : interactive.path("list_reply").path("title").asText(null));
            }
            case "button" -> nonBlank(message.path("button").path("text").asText(null));
            default -> null;
        };
    }

    private String extractInteractiveId(JsonNode message) {
        JsonNode interactive = message.path("interactive");
        String id = interactive.path("button_reply").path("id").asText(null);
        if (id == null || id.isBlank()) id = interactive.path("list_reply").path("id").asText(null);
        if ((id == null || id.isBlank()) && "button".equals(message.path("type").asText())) id = message.path("button").path("payload").asText(null);
        return nonBlank(id);
    }

    private boolean isMediaMessage(String type) {
        return "image".equals(type) || "audio".equals(type) || "video".equals(type)
                || "document".equals(type) || "sticker".equals(type);
    }

    private boolean isGeminiSupportedMedia(String mimeType) {
        return mimeType != null && (mimeType.startsWith("image/")
                || mimeType.startsWith("audio/") || mimeType.startsWith("video/")
                || "application/pdf".equalsIgnoreCase(mimeType));
    }

    private String nonBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String unsupportedMessageReply(String type) {
        return switch (type) {
            case "location" -> "Thanks for sharing your location. What would you like help with?";
            case "reaction" -> null;
            case "contacts" -> "I received the contact. Please tell me what you'd like help with.";
            default -> "I can help with clinic information, consultation hours, fees, and appointment availability. Please send your question as a text message.";
        };
    }
}
