package com.doctor.clinic.DoctorClinic.AIServices;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

@Service
public class WhatsappServiceImpl {

    @Value("${whatsapp.phone.number.id}")
    private String phoneNumberId;

    @Value("${whatsapp.access.token}")
    private String accessToken;

    @Value("${facebook.graph-version}")
    private String graphVersion;

    private final WebClient webClient = WebClient.builder().build();

    public void sendMessage(String toNumber, String message) {

        Map<String, Object> requestBody = new HashMap<>();

        requestBody.put("messaging_product", "whatsapp");
        requestBody.put("to", toNumber);
        requestBody.put("type", "text");

        requestBody.put(
            "text",
            Map.of("body", message)
        );

        String url =
            "https://graph.facebook.com/"
            + graphVersion
            + "/"
            + phoneNumberId
            + "/messages";

        System.out.println("========== WHATSAPP SEND ==========");
        System.out.println("Graph Version = " + graphVersion);
        System.out.println("Phone Number ID = " + phoneNumberId);
        System.out.println("To Number = " + toNumber);
        System.out.println("URL = " + url);

        try {

            String response = webClient.post()
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

            System.out.println("WhatsApp response = " + response);
            System.out.println("Message sent successfully!");

        } catch (WebClientResponseException e) {

            System.err.println(
                "WhatsApp API error. HTTP Status = "
                + e.getStatusCode()
            );

            System.err.println(
                "Response Body = "
                + e.getResponseBodyAsString()
            );

        } catch (Exception e) {

            System.err.println(
                "Error sending WhatsApp message: "
                + e.getMessage()
            );

            e.printStackTrace();
        }
    }
}