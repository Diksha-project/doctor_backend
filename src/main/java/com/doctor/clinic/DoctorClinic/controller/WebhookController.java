package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.AIServices.GeminiService;
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
 private static final String VERIFY_TOKEN = "EAANNWLz8YiMBRZA0Qu3Gge7IMCZA1leDIaZCzEhx87tzDVLtQu3O0Tx4FJCKdfVdNlDrtQiIRdKguNcP0CVJZAZB3XlAFTOpCcfA0Ad4qkCIVlKPE2TC006uNwzorEUbucZBTIprcXbq6SQjV2Lhong7evNebrhvpQqVmrkam1V2ieCTTbl8vV3UZAZBwfnLIxfOU38yXGmsl41ZAcZCBhW0cuzZAeZBda1nLig22gOQ9iAbPh48mZCnI7nQ2ZAbM96dLOCryw6CcNAZBcxixOjfUkGJhZBJ";
 
 private final DoctorRepo doctorRepo;
 private final GeminiService aiService;
  WhatsappServiceImpl whatsAppService;
 private final ObjectMapper objectMapper;
 
 public WebhookController(DoctorRepo doctorRepo, GeminiService aiService, 
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
             
             System.out.println("From: " + fromNumber + ", Message: " + messageText);
             
             // Find doctor and get AI response
          if (doctorNumber != null && doctorNumber.length() >= 10) {
            doctorNumber = doctorNumber.substring(doctorNumber.length() - 10);
           }
             Doctor doctor = doctorRepo.findByPhoneNumber(doctorNumber);
             if (doctor != null) {
                 String aiResponse = aiService.generateResponse(messageText, doctor);
                 whatsAppService.sendMessage(fromNumber, aiResponse);
             }
         }
         
     } catch (Exception e) {
         System.err.println("Error: " + e.getMessage());
         e.printStackTrace();
     }
     
     return ResponseEntity.ok("EVENT_RECEIVED");
 }
}
