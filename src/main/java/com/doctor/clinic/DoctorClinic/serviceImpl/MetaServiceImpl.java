package com.doctor.clinic.DoctorClinic.serviceImpl;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.response.PhoneNumberResponse;
import com.doctor.clinic.DoctorClinic.service.MetaService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetaServiceImpl implements MetaService {

    private final DoctorRepo doctorRepo;

    private final RestTemplate restTemplate;

    @Value("${facebook.app.id}")
    private String appId;

    @Value("${facebook.app.secret}")
    private String appSecret;

    @Value("${facebook.graph-version}")
    private String version;

    @Override
    public void connectDoctor(String email, String code) {

        System.out.println("=================================");
        System.out.println("META CONNECT STARTED");
        System.out.println("EMAIL = " + email);
        System.out.println("CODE PRESENT = "
                + (code != null && !code.isBlank()));
        System.out.println("=================================");

        Doctor doctor = doctorRepo.findByEmail(email);

        if (doctor == null) {
            throw new RuntimeException(
                    "Doctor not found for email: " + email);
        }

        System.out.println("STEP 1: Doctor found");

        String accessToken = exchangeCode(code);

        System.out.println("STEP 2: Access token received");

        String wabaId = getWabaId(accessToken);

        System.out.println("STEP 3: WABA ID received = " + wabaId);

        PhoneNumberResponse phone =
                getPhoneNumber(accessToken, wabaId);

        System.out.println("STEP 4: Phone number received");
        System.out.println("Phone ID = " + phone.getId());

        doctor.setWhatsappAccessToken(accessToken);
        doctor.setWhatsappPhoneNumberId(phone.getId());
        doctor.setWhatsappNumber(phone.getDisplayPhoneNumber());
        doctor.setWhatsappActivated(true);

        doctorRepo.save(doctor);

        System.out.println("STEP 5: Doctor saved successfully");
        System.out.println("META CONNECT COMPLETED");
    }
    private String exchangeCode(String code) {

        String url =
                "https://graph.facebook.com/"
                        + version
                        + "/oauth/access_token";

        System.out.println("========== META EXCHANGE CODE ==========");
        System.out.println("Graph Version = " + version);
        System.out.println("App ID present = " + (appId != null && !appId.isBlank()));
        System.out.println("App Secret present = " + (appSecret != null && !appSecret.isBlank()));
        System.out.println("Code present = " + (code != null && !code.isBlank()));

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("client_id", appId)
                        .queryParam("client_secret", appSecret)
                        .queryParam("code", code);

        try {

            ResponseEntity<JsonNode> response =
                    restTemplate.getForEntity(
                            builder.toUriString(),
                            JsonNode.class);

            System.out.println("Meta exchange HTTP status = "
                    + response.getStatusCode());

            System.out.println("Meta exchange response = "
                    + response.getBody());

            JsonNode body = response.getBody();

            if (body == null || body.path("access_token").isMissingNode()) {
                throw new RuntimeException(
                        "Meta did not return access_token. Response = " + body);
            }

            return body.path("access_token").asText();

        } catch (Exception e) {

            System.err.println("========== META EXCHANGE FAILED ==========");
            e.printStackTrace();

            throw new RuntimeException(
                    "Failed to exchange Meta authorization code: "
                            + e.getMessage(), e);
        }
    }
    
    private String getWabaId(String accessToken) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        String url =
                "https://graph.facebook.com/"
                        + version
                        + "/debug_token";

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("input_token", accessToken);

        try {

            ResponseEntity<JsonNode> response =
                    restTemplate.exchange(
                            builder.toUriString(),
                            HttpMethod.GET,
                            entity,
                            JsonNode.class);

            System.out.println("========== DEBUG TOKEN ==========");
            System.out.println("HTTP STATUS = " + response.getStatusCode());
            System.out.println("RESPONSE = " + response.getBody());

            JsonNode body = response.getBody();

            if (body == null) {
                throw new RuntimeException(
                        "Empty response from Meta debug_token");
            }

            JsonNode granularScopes =
                    body.path("data").path("granular_scopes");

            if (!granularScopes.isArray() || granularScopes.isEmpty()) {

                throw new RuntimeException(
                        "Meta debug_token response does not contain granular_scopes. "
                                + "Response = " + body);
            }

            for (JsonNode scope : granularScopes) {

                System.out.println("SCOPE = " + scope);

                JsonNode targetIds =
                        scope.path("target_ids");

                if (targetIds.isArray() && !targetIds.isEmpty()) {

                    String wabaId =
                            targetIds.get(0).asText();

                    System.out.println("WABA ID = " + wabaId);

                    return wabaId;
                }
            }

            throw new RuntimeException(
                    "No WABA ID found in Meta debug_token response");

        } catch (Exception e) {

            System.err.println("========== GET WABA ID FAILED ==========");
            e.printStackTrace();

            throw new RuntimeException(
                    "Failed to get WABA ID: "
                            + e.getMessage(), e);
        }
    }
    
    private PhoneNumberResponse getPhoneNumber(
            String accessToken,
            String wabaId) {

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        String url =
                "https://graph.facebook.com/"
                        + version
                        + "/"
                        + wabaId
                        + "/phone_numbers";

        try {

            ResponseEntity<JsonNode> response =
                    restTemplate.exchange(
                            url,
                            HttpMethod.GET,
                            entity,
                            JsonNode.class);

            System.out.println("========== PHONE NUMBERS ==========");
            System.out.println("WABA ID = " + wabaId);
            System.out.println("HTTP STATUS = " + response.getStatusCode());
            System.out.println("RESPONSE = " + response.getBody());

            JsonNode data =
                    response.getBody().path("data");

            if (!data.isArray() || data.isEmpty()) {

                throw new RuntimeException(
                        "Meta returned no WhatsApp phone numbers. "
                                + "Response = " + response.getBody());
            }

            JsonNode phone = data.get(0);

            PhoneNumberResponse p =
                    new PhoneNumberResponse();

            p.setId(
                    phone.path("id").asText()
            );

            p.setDisplayPhoneNumber(
                    phone.path("display_phone_number").asText()
            );

            return p;

        } catch (Exception e) {

            System.err.println("========== GET PHONE NUMBER FAILED ==========");
            e.printStackTrace();

            throw new RuntimeException(
                    "Failed to get WhatsApp phone number: "
                            + e.getMessage(), e);
        }
    }
}
