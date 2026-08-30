package com.doctor.clinic.DoctorClinic.AIServices;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.doctor.clinic.DoctorClinic.entity.Doctor;

@Service
public class WhatsappServiceImpl {

	@Value("${facebook.graph-version}")
    private String graphVersion;

    private final WebClient webClient =
            WebClient.builder().build();

    /**
     * Send WhatsApp message using the
     * doctor's connected WhatsApp account.
     */
    public void sendMessage(
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

            return;
        }

        if (accessToken == null
                || accessToken.isBlank()) {

            System.err.println(
                    "WhatsApp Access Token is missing "
                    + "for Doctor ID = "
                    + doctor.getId());

            return;
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
                "To Number = "
                + toNumber);

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

        } catch (Exception e) {

            System.err.println(
                    "Error sending WhatsApp message = "
                    + e.getMessage());

            e.printStackTrace();
        }
    }
}