package com.doctor.clinic.DoctorClinic.AIServices;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.doctor.clinic.DoctorClinic.entity.Doctor;

@Service
public class WhatsappServiceImpl {

	@Value("${facebook.graph-version}")
    private String graphVersion;

    private final WebClient webClient =
            WebClient.builder().build();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public record DownloadedMedia(byte[] bytes, String mimeType) {}
    public record SendResult(boolean sent, String providerMessageId) {}

    /** Downloads an incoming WhatsApp attachment using Meta's media URL flow. */
    public DownloadedMedia downloadMedia(String mediaId, String accessToken) {
        String metadataJson = webClient.get()
                .uri("https://graph.facebook.com/" + graphVersion + "/" + mediaId)
                .header("Authorization", "Bearer " + accessToken)
                .retrieve()
                .bodyToMono(String.class)
                .block();

        try {
            JsonNode metadata = objectMapper.readTree(metadataJson);
            String downloadUrl = metadata.path("url").asText(null);
            String mimeType = metadata.path("mime_type").asText(null);
            long fileSize = metadata.path("file_size").asLong(0);
            if (downloadUrl == null || mimeType == null) {
                throw new IllegalArgumentException("Meta did not return media URL or MIME type");
            }
            if (fileSize > 14_000_000) {
                throw new IllegalArgumentException("Attachment exceeds the supported size");
            }
            byte[] bytes = webClient.get()
                    .uri(downloadUrl)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .block();
            if (bytes == null || bytes.length == 0 || bytes.length > 14_000_000) {
                throw new IllegalArgumentException("Attachment is empty or exceeds the supported size");
            }
            return new DownloadedMedia(bytes, mimeType);
        } catch (Exception e) {
            throw new IllegalStateException("Could not read WhatsApp media metadata", e);
        }
    }

    /**
     * Send WhatsApp message using the
     * doctor's connected WhatsApp account.
     */
    public boolean sendMessage(
            Doctor doctor,
            String toNumber,
            String message) {
        return sendMessageWithResult(doctor, toNumber, message).sent();
    }

    public SendResult sendMessageWithResult(Doctor doctor, String toNumber, String message) {

        /*
         * =====================================================
         * GET WHATSAPP DETAILS FROM doctors_details
         * =====================================================
         */

        String phoneNumberId =
                doctor.getWhatsappPhoneNumberId();

        String accessToken =
                doctor.getWhatsappAccessToken();

        /*
         * =====================================================
         * VALIDATION
         * =====================================================
         */

        if (phoneNumberId == null
                || phoneNumberId.isBlank()) {

            System.err.println(
                    "WhatsApp Phone Number ID is missing "
                    + "for Doctor ID = "
                    + doctor.getId());

            return new SendResult(false, null);
        }

        if (accessToken == null
                || accessToken.isBlank()) {

            System.err.println(
                    "WhatsApp Access Token is missing "
                    + "for Doctor ID = "
                    + doctor.getId());

            return new SendResult(false, null);
        }

        /*
         * =====================================================
         * REQUEST BODY
         * =====================================================
         */

        Map<String, Object> requestBody =
                new HashMap<>();

        requestBody.put(
                "messaging_product",
                "whatsapp"
        );

        requestBody.put(
                "to",
                toNumber
        );

        requestBody.put(
                "type",
                "text"
        );

        requestBody.put(
                "text",
                Map.of(
                        "body",
                        message
                )
        );

        /*
         * =====================================================
         * META URL
         *
         * POST:
         *
         * /{PHONE_NUMBER_ID}/messages
         * =====================================================
         */

        String url =
                "https://graph.facebook.com/"
                + graphVersion
                + "/"
                + phoneNumberId
                + "/messages";

        /*
         * =====================================================
         * LOGGING
         * =====================================================
         */

        System.out.println(
                "=================================");

        System.out.println(
                "WHATSAPP SEND MESSAGE");

        System.out.println(
                "=================================");

        System.out.println(
                "Doctor ID = "
                + doctor.getId());

        System.out.println(
                "Doctor Name = "
                + doctor.getFullName());

        System.out.println(
                "Graph Version = "
                + graphVersion);

        System.out.println(
                "Phone Number ID = "
                + phoneNumberId);

        System.out.println(
                "URL = "
                + url);

        /*
         * =====================================================
         * CALL META WHATSAPP API
         * =====================================================
         */

        try {

            String response =
                    webClient.post()
                            .uri(url)
                            .header(
                                    "Authorization",
                                    "Bearer " + accessToken
                            )
                            .header(
                                    "Content-Type",
                                    "application/json"
                            )
                            .bodyValue(requestBody)
                            .retrieve()
                            .bodyToMono(String.class)
                            .block();

            String providerMessageId = null;
            if (response != null) {
                try {
                    providerMessageId = objectMapper.readTree(response)
                            .path("messages").path(0).path("id").asText(null);
                } catch (Exception ignored) {
                    // A successful send remains successful if Meta omits an optional response ID.
                }
            }
            return new SendResult(true, providerMessageId);

        } catch (WebClientResponseException e) {

            System.err.println(
                    "=================================");

            System.err.println(
                    "WHATSAPP API ERROR");

            System.err.println(
                    "=================================");

            System.err.println(
                    "HTTP Status = "
                    + e.getStatusCode());

            return new SendResult(false, null);

        } catch (Exception e) {

            System.err.println("Error sending WhatsApp message");
            return new SendResult(false, null);
        }
    }

    /** Uploads a file to Meta and sends it to the patient as an image or document message. */
    public SendResult sendMediaWithResult(Doctor doctor, String toNumber, byte[] bytes, String mimeType,
                                          String filename, String caption) {
        String phoneNumberId = doctor.getWhatsappPhoneNumberId();
        String accessToken = doctor.getWhatsappAccessToken();
        if (phoneNumberId == null || phoneNumberId.isBlank() || accessToken == null || accessToken.isBlank()) {
            return new SendResult(false, null);
        }
        try {
            org.springframework.http.client.MultipartBodyBuilder builder =
                    new org.springframework.http.client.MultipartBodyBuilder();
            builder.part("messaging_product", "whatsapp");
            builder.part("type", mimeType);
            builder.part("file", new org.springframework.core.io.ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    return filename;
                }
            }).contentType(org.springframework.http.MediaType.parseMediaType(mimeType));

            String uploadResponse = webClient.post()
                    .uri("https://graph.facebook.com/" + graphVersion + "/" + phoneNumberId + "/media")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(builder.build()))
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            String mediaId = objectMapper.readTree(uploadResponse).path("id").asText(null);
            if (mediaId == null) {
                return new SendResult(false, null);
            }

            boolean image = mimeType.startsWith("image/");
            Map<String, Object> media = new HashMap<>();
            media.put("id", mediaId);
            if (caption != null && !caption.isBlank()) {
                media.put("caption", caption);
            }
            if (!image) {
                media.put("filename", filename);
            }
            Map<String, Object> body = new HashMap<>();
            body.put("messaging_product", "whatsapp");
            body.put("to", toNumber);
            body.put("type", image ? "image" : "document");
            body.put(image ? "image" : "document", media);

            String response = webClient.post()
                    .uri("https://graph.facebook.com/" + graphVersion + "/" + phoneNumberId + "/messages")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Content-Type", "application/json")
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();
            String providerId = objectMapper.readTree(response).path("messages").path(0).path("id").asText(null);
            return new SendResult(true, providerId);
        } catch (Exception e) {
            System.err.println("Error sending WhatsApp media: " + e.getMessage());
            return new SendResult(false, null);
        }
    }

    public boolean sendInteractiveMessage(Doctor doctor, String toNumber, Map<String, Object> interactive) {
        return sendInteractiveWithResult(doctor, toNumber, interactive).sent();
    }

    public SendResult sendInteractiveWithResult(Doctor doctor, String toNumber, Map<String, Object> interactive) {
        String url = "https://graph.facebook.com/" + graphVersion + "/" + doctor.getWhatsappPhoneNumberId() + "/messages";
        Map<String,Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp"); body.put("to", toNumber); body.put("type", "interactive"); body.put("interactive", interactive);
        try {
            String response = webClient.post().uri(url).header("Authorization", "Bearer " + doctor.getWhatsappAccessToken())
                    .header("Content-Type", "application/json").bodyValue(body).retrieve().bodyToMono(String.class).block();
            String providerMessageId = null;
            if (response != null) {
                try {
                    providerMessageId = objectMapper.readTree(response).path("messages").path(0).path("id").asText(null);
                } catch (Exception ignored) {
                    // A successful send remains successful if Meta omits an optional response ID.
                }
            }
            return new SendResult(true, providerMessageId);
        } catch (Exception e) {
            System.err.println("Could not send WhatsApp interactive message: " + e.getMessage());
            return new SendResult(false, null);
        }
    }
}
