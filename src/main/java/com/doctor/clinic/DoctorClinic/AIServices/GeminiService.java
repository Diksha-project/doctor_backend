package com.doctor.clinic.DoctorClinic.AIServices;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.entity.Doctor;



import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.reactive.function.client.WebClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class GeminiService {




	private String model = "gemini-2.5-flash";

    @Value("${gemini.api.key}")
    private String apiKey;
	    
	    private final WebClient webClient;
	    private final ObjectMapper objectMapper;
	    
	    public GeminiService() {
	        this.webClient = WebClient.builder().build();
	        this.objectMapper = new ObjectMapper();
	    }
	  
	    public String generateResponse(String patientMessage, Doctor doctor) {
	    	
	    	  
		  
	        if (apiKey == null || apiKey.isEmpty()) {
	            return getFallbackResponse(doctor);
	        }
	        
	        try {
	            String prompt = buildPrompt(patientMessage, doctor);
	            
	            Map<String, Object> requestBody = new HashMap<>();
	            Map<String, Object> content = new HashMap<>();
	            content.put("parts", List.of(Map.of("text", prompt)));
	            requestBody.put("contents", List.of(content));
	            
	            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
	            
	            String response = webClient.post()
	                .uri(url)
	                .header("Content-Type", "application/json")
	                .bodyValue(requestBody)
	                .retrieve()
	                .bodyToMono(String.class)
	                .block();
	            
	            JsonNode jsonNode = objectMapper.readTree(response);
	            return jsonNode
	                .path("candidates")
	                .get(0)
	                .path("content")
	                .path("parts")
	                .get(0)
	                .path("text")
	                .asText();
	            
	        } catch (Exception e) {
	            log.error("AI Error: {}", e.getMessage());
	            return getFallbackResponse(doctor);
	        }
	    }
	    
	    private String buildPrompt(String message, Doctor doctor) {
	        return String.format("""
	            You are Dr. %s's assistant. Respond to this patient message.
	            
	            Doctor Info:
	            - Name: Dr. %s
	            - Specialization: %s
	            - Fee: ₹%s
	            - Hours: %s
	            
	            Patient Message: %s
	            
	            Rules:
	            - Be polite and short (2-3 sentences)
	            - Don't give medical advice
	            - Mention fee if asked
	            """,
	            doctor.getFullName(),
	            doctor.getFullName(),
	            doctor.getSpecialization() != null ? doctor.getSpecialization() : "General Physician",
	            doctor.getConsultationFee(),
	            doctor.getConsultationHours(),
	            message
	        );
	    }
	    
	    private String getFallbackResponse(Doctor doctor) {
	        return String.format("Dr. %s is available %s. Fee: ₹%s. Would you like to book an appointment?",
	            doctor.getFullName(),
	            doctor.getConsultationHours(),
	            doctor.getConsultationFee()
	        );
	    }
	}