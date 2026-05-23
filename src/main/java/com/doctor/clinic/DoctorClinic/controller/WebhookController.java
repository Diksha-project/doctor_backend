package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

//import com.doctor.clinic.DoctorClinic.AIServices.GeminiService;
import com.doctor.clinic.DoctorClinic.AIServices.GeminiServiceLatest;
import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

//WebhookController.java
@RestController
@RequestMapping("/webhook")
public class WebhookController {
 // This value MUST match the one you set in the Meta Dashboard.
 private static final String VERIFY_TOKEN = "EAANNWLz8YiMBRVqoIlPCwqc7MwYTcX6N2NnBhOhfyIODvKL5ncdsqaphpiOQUKYh8Q7DsS9WdKoZCUx5OiIZADZBzJkKQGIsDHCCZBiZA9d3FuZCEZBnsC8zsK1vlZBnKSMZCZCSj4RZC1T2ZAMX3z9J4DldYeflhJyleK2fMNZBLwh5Yjpy9ZAEyZCZCAuxBxund83TumsFcZCtZCiavrCQ9BsJUkjkWGo7TlZBlUHe4hzJBSlix7YFQvha4Q3TK7vJ7o70XtKsR9gwnAIzbH1g4BXdiSIZBOuLEn0ZD";
 private final DoctorRepo doctorRepo;
 private final GeminiServiceLatest aiService;
  WhatsappServiceImpl whatsAppService;
 private final ObjectMapper objectMapper;
 
 public WebhookController(DoctorRepo doctorRepo, GeminiServiceLatest aiService, 
                          WhatsappServiceImpl whatsAppService, ObjectMapper objectMapper) {
     this.doctorRepo = doctorRepo;
     this.aiService = aiService;
     this.whatsAppService = whatsAppService;
     this.objectMapper = objectMapper;
 }

 @GetMapping("/whatsapp")
 public String verifyWebhook(@RequestParam("hub.mode") String mode,
                             @RequestParam("hub.verify_token") String token,
                             @RequestParam("hub.challenge") String challenge) {
     if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
         return challenge;
     }
     return "Verification failed";
 }
 
 
 @PostMapping("/whatsapp")
 public ResponseEntity<String> handleIncomingMessages(@RequestBody String payload) {
     System.out.println("Webhook received: " + payload);
     
     try {
         JsonNode json = objectMapper.readTree(payload);
         
         // Extract message details
         JsonNode messages = json.path("entry").get(0)
                                 .path("changes").get(0)
                                 .path("value").path("messages");
         
         if (messages.isArray() && messages.size() > 0) {
             JsonNode message = messages.get(0);
             String fromNumber = message.path("from").asText();
             String messageText = message.path("text").path("body").asText();
             String doctorNumber = json.path("entry").get(0)
                                       .path("changes").get(0)
                                       .path("value").path("metadata")
                                       .path("display_phone_number").asText();
             if (doctorNumber != null && doctorNumber.length() >= 10) {
            	    doctorNumber = doctorNumber.substring(doctorNumber.length() - 10);
            	}
             
             System.out.println("From: " + fromNumber + ", Message: " + messageText);
             
             // Find doctor and get AI response
             System.out.println(fromNumber);
             Doctor doctor = doctorRepo.findByPhoneNumber(doctorNumber);
             if (doctor != null) {
            	 System.out.println("Calling AI service...");
                 String aiResponse = aiService.generateResponse(messageText, doctor);
                 System.out.println("Calling AI service completed...");
                 String testNumber = "919584352846";
                 whatsAppService.sendMessage(testNumber, aiResponse);
                 //whatsAppService.sendMessage(fromNumber, aiResponse);
                 System.out.println("sending mest to user whatspp");
             }else{
            	 System.out.println("doctor not presnt");
             }
         }
         
     } catch (Exception e) {
         System.err.println("Error: " + e.getMessage());
         e.printStackTrace();
     }
     
     return ResponseEntity.ok("EVENT_RECEIVED");
 }
}
