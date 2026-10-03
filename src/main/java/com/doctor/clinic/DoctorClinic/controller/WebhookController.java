package com.doctor.clinic.DoctorClinic.controller;

import java.util.Optional;

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

    public WebhookController(
            DoctorRepo doctorRepo,
            GeminiServiceLatest aiService,
            WhatsappServiceImpl whatsAppService,
            ObjectMapper objectMapper,
            IntentDetector intentDetector,
            ProcessedWhatsappMessageRepo processedMessageRepo,
            PatientProfileService patientProfileService,
            PatientChatHistoryService patientChatHistoryService,
            AppointmentBookingChatService appointmentBookingChatService) {

        this.doctorRepo = doctorRepo;
        this.aiService = aiService;
        this.whatsAppService = whatsAppService;
        this.objectMapper = objectMapper;
        this.intentDetector = intentDetector;
        this.processedMessageRepo = processedMessageRepo;
        this.patientProfileService = patientProfileService;
        this.patientChatHistoryService = patientChatHistoryService;
        this.appointmentBookingChatService = appointmentBookingChatService;
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
        String recentHistory = patientChatHistoryService.recentPromptContext(patient.getId());
        String interactiveId = extractInteractiveId(message);
        if ("text".equals(type) || "interactive".equals(type) || "button".equals(type)) {
            Optional<AppointmentBookingChatService.Reply> booking = appointmentBookingChatService.handle(doctor, patient, text, interactiveId);
            if (booking.isPresent()) {
                patientChatHistoryService.recordInbound(patient, doctor, messageId, type, text, null, null, null);
                AppointmentBookingChatService.Reply reply = booking.get();
                boolean sent = reply.interactive() == null
                        ? whatsAppService.sendMessage(doctor, fromNumber, reply.text())
                        : whatsAppService.sendInteractiveMessage(doctor, fromNumber, reply.interactive());
                if (sent) patientChatHistoryService.recordOutbound(patient, doctor, messageId, reply.text());
                return;
            }
        }
        String response;
        byte[] attachmentData = null;
        String attachmentMimeType = null;
        String attachmentName = null;
        String storedInboundText = text;
        if (isMediaMessage(type)) {
            JsonNode mediaNode = message.path(type);
            String mediaId = mediaNode.path("id").asText(null);
            String caption = mediaNode.path("caption").asText("");
            storedInboundText = caption.isBlank() ? "[Sent a " + type + " attachment]" : caption;
            attachmentMimeType = mediaNode.path("mime_type").asText(null);
            attachmentName = mediaNode.path("filename").asText(null);
            try {
                if (mediaId == null || mediaId.isBlank()) {
                    throw new IllegalArgumentException("Media ID missing");
                }
                WhatsappServiceImpl.DownloadedMedia media =
                        whatsAppService.downloadMedia(mediaId, doctor.getWhatsappAccessToken());
                attachmentData = media.bytes();
                attachmentMimeType = media.mimeType();
                if (!isGeminiSupportedMedia(media.mimeType())) {
                    response = "I received the attachment, but its file type can't be read here. Please send an image, voice note, video, or PDF, or paste the relevant text.";
                } else {
                    String mediaPrompt = caption.isBlank()
                            ? "The patient sent a " + type + " attachment. Please understand it and help with its clinic-related content."
                            : "The patient sent a " + type + " attachment with this message: " + caption;
                    response = aiService.generateResponse(mediaPrompt, doctor, recentHistory,
                            media.bytes(), media.mimeType());
                }
            } catch (Exception e) {
                System.err.println("Could not process WhatsApp " + type + " attachment: " + e.getMessage());
                response = "I received your attachment, but couldn't open it just now. Please try again or send your question as text.";
            }
        } else if (text == null) {
            response = unsupportedMessageReply(type);
            if (storedInboundText == null) storedInboundText = "[Sent a " + type + " message]";
        } else {
            Intent intent = intentDetector.detect(text);
            if (intent == Intent.GREETING) {
                response = "Hello! I am " + doctor.getFullName()
                        + "'s virtual assistant. How can I help you with the clinic?";
            } else if (intent == Intent.THANKS) {
                response = "You're welcome! Is there anything else I can help you with?";
            } else if (intent == Intent.GOODBYE) {
                response = "Thank you for contacting the clinic. Have a great day!";
            } else {
                response = aiService.generateResponse(text, doctor, recentHistory, null, null);
            }
        }

        patientChatHistoryService.recordInbound(patient, doctor, messageId, type,
                storedInboundText, attachmentMimeType, attachmentName, attachmentData);

        if (response != null && !response.isBlank()
                && whatsAppService.sendMessage(doctor, fromNumber, response)) {
            patientChatHistoryService.recordOutbound(patient, doctor, messageId, response);
        }
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
