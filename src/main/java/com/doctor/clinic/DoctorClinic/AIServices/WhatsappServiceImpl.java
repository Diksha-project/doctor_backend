package com.doctor.clinic.DoctorClinic.AIServices;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.fasterxml.jackson.databind.ObjectMapper;


@Service
public class WhatsappServiceImpl {
    @Value("${whatsapp.phone.number.id}")
    private String phoneNumberId;

    @Value("${whatsapp.access.token}")
    private String accessToken;

    private final WebClient webClient = WebClient.builder().build();

    public void sendMessage(String toNumber, String message) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("messaging_product", "whatsapp");
        requestBody.put("to", toNumber);
        requestBody.put("type", "text");
        requestBody.put("text", Map.of("body", message));

        try {
            webClient.post()
                .uri("https://graph.facebook.com/v22.0/" + phoneNumberId + "/messages")
                .header("Authorization", "Bearer " + accessToken)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)  // Change to String to see error response
                .block();  // Use block() instead of subscribe()
            System.out.println("Message sent successfully!");
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            if (e instanceof WebClientResponseException) {
                WebClientResponseException we = (WebClientResponseException) e;
                System.err.println("Response Body: " + we.getResponseBodyAsString());
            }
        }
    }
}


