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

            return false;
        }

        if (accessToken == null
                || accessToken.isBlank()) {

            System.err.println(
                    "WhatsApp Access Token is missing "
                    + "for Doctor ID = "
                    + doctor.getId());

            return false;
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

            System.out.println(
                    "WhatsApp API Response = "
                    + response);

            System.out.println(
                    "WhatsApp message sent successfully");
            return true;

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

            System.err.println(
                    "Response Body = "
                    + e.getResponseBodyAsString());
            return false;

        } catch (Exception e) {

            System.err.println(
                    "Error sending WhatsApp message = "
                    + e.getMessage());

            e.printStackTrace();
            return false;
        }
    }

    public boolean sendInteractiveMessage(Doctor doctor, String toNumber, Map<String, Object> interactive) {
        String url = "https://graph.facebook.com/" + graphVersion + "/" + doctor.getWhatsappPhoneNumberId() + "/messages";
        Map<String,Object> body = new HashMap<>();
        body.put("messaging_product", "whatsapp"); body.put("to", toNumber); body.put("type", "interactive"); body.put("interactive", interactive);
        try {
            webClient.post().uri(url).header("Authorization", "Bearer " + doctor.getWhatsappAccessToken())
                    .header("Content-Type", "application/json").bodyValue(body).retrieve().bodyToMono(String.class).block();
            return true;
        } catch (Exception e) {
            System.err.println("Could not send WhatsApp interactive message: " + e.getMessage());
            return false;
        }
    }
}
