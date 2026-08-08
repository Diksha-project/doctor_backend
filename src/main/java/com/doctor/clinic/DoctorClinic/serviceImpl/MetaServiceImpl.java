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
import com.google.api.client.util.Value;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MetaServiceImpl implements MetaService {

    private final DoctorRepo doctorRepo;

    private final RestTemplate restTemplate;

    @Value("${meta.app-id}")
    private String appId;

    @Value("${meta.app-secret}")
    private String appSecret;

    @Value("${meta.graph-version}")
    private String version;

    @Override
    public void connectDoctor(String email,String code) {

    	Doctor doctor = doctorRepo.findByEmail(email);
    	      

        String accessToken = exchangeCode(code);

        String wabaId = getWabaId(accessToken);

        PhoneNumberResponse phone = getPhoneNumber(accessToken,wabaId);

        doctor.setWhatsappAccessToken(accessToken);

        doctor.setWhatsappPhoneNumberId(phone.getId());

        doctor.setWhatsappNumber(phone.getDisplayPhoneNumber());

        doctor.setWhatsappActivated(true);

        doctorRepo.save(doctor);

    }
    
    private String exchangeCode(String code) {

        String url =
                "https://graph.facebook.com/"
                        + version
                        + "/oauth/access_token";

        UriComponentsBuilder builder =
                UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("client_id",appId)
                        .queryParam("client_secret",appSecret)
                        .queryParam("code",code);

        ResponseEntity<JsonNode> response =
                restTemplate.getForEntity(
                        builder.toUriString(),
                        JsonNode.class);

        return response.getBody()
                .get("access_token")
                .asText();

    }
    
    private String getWabaId(String accessToken) {

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response =
                restTemplate.exchange(

                        "https://graph.facebook.com/"
                                + version
                                + "/debug_token?input_token="
                                + accessToken,

                        HttpMethod.GET,

                        entity,

                        JsonNode.class);

        return response.getBody()
                .path("data")
                .path("granular_scopes")
                .get(0)
                .path("target_ids")
                .get(0)
                .asText();

    }
    
    private PhoneNumberResponse getPhoneNumber(
            String accessToken,
            String wabaId) {

        HttpHeaders headers = new HttpHeaders();

        headers.setBearerAuth(accessToken);

        HttpEntity<Void> entity =
                new HttpEntity<>(headers);

        ResponseEntity<JsonNode> response =
                restTemplate.exchange(

                        "https://graph.facebook.com/"
                                + version
                                + "/"
                                + wabaId
                                + "/phone_numbers",

                        HttpMethod.GET,

                        entity,

                        JsonNode.class);

        JsonNode phone =
                response.getBody()
                        .get("data")
                        .get(0);

        PhoneNumberResponse p =
                new PhoneNumberResponse();

        p.setId(phone.get("id").asText());

        p.setDisplayPhoneNumber(
                phone.get("display_phone_number").asText());

        return p;

    }
}
