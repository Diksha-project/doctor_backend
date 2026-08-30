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

import java.util.Optional;

//WebhookController.java
@RestController
@RequestMapping("/webhook")
public class WebhookController {
 // This value MUST match the one you set in the Meta Dashboard.
 private static final String VERIFY_TOKEN = "EAANNWLz8YiMBRVqoIlPCwqc7MwYTcX6N2NnBhOhfyIODvKL5ncdsqaphpiOQUKYh8Q7DsS9WdKoZCUx5OiIZADZBzJkKQGIsDHCCZBiZA9d3FuZCEZBnsC8zsK1vlZBnKSMZCZCSj4RZC1T2ZAMX3z9J4DldYeflhJyleK2fMNZBLwh5Yjpy9ZAEyZCZCAuxBxund83TumsFcZCtZCiavrCQ9BsJUkjkWGo7TlZBlUHe4hzJBSlix7YFQvha4Q3TK7vJ7o70XtKsR9gwnAIzbH1g4BXdiSIZBOuLEn0ZD";
 private final DoctorRepo doctorRepo;
 private final GeminiServiceLatest aiService;
 private final WhatsappServiceImpl whatsAppService;
 private final ObjectMapper objectMapper;

 public WebhookController(
         DoctorRepo doctorRepo,
         GeminiServiceLatest aiService,
         WhatsappServiceImpl whatsAppService,
         ObjectMapper objectMapper) {

     this.doctorRepo = doctorRepo;
     this.aiService = aiService;
     this.whatsAppService = whatsAppService;
     this.objectMapper = objectMapper;
 }

 /**
  * Meta Webhook Verification
  */
 @GetMapping("/whatsapp")
 public String verifyWebhook(
         @RequestParam("hub.mode") String mode,
         @RequestParam("hub.verify_token") String token,
         @RequestParam("hub.challenge") String challenge) {

     System.out.println("Inside /webhook/whatsapp");

     if ("subscribe".equals(mode)
             && VERIFY_TOKEN.equals(token)) {

         System.out.println("Webhook verification successful");

         return challenge;
     }

     System.out.println("Webhook verification failed");

     return "Verification failed";
 }

 /**
  * WhatsApp Incoming Message Webhook
  */
 @PostMapping("/whatsapp")
 public ResponseEntity<String> handleIncomingMessages(
         @RequestBody String payload) {

     System.out.println("=================================");
     System.out.println("WHATSAPP WEBHOOK RECEIVED");
     System.out.println("=================================");

     System.out.println("Webhook payload = " + payload);

     try {

         JsonNode json =
                 objectMapper.readTree(payload);

         JsonNode entry =
                 json.path("entry").get(0);

         if (entry == null || entry.isMissingNode()) {
             System.out.println("Entry not found");
             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         JsonNode change =
                 entry.path("changes").get(0);

         if (change == null || change.isMissingNode()) {
             System.out.println("Change not found");
             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         JsonNode value =
                 change.path("value");

         /*
          * =====================================================
          * STEP 1: CHECK WHETHER THIS WEBHOOK CONTAINS MESSAGE
          * =====================================================
          */

         JsonNode messages =
                 value.path("messages");

         if (!messages.isArray()
                 || messages.isEmpty()) {

             System.out.println(
                     "Webhook does not contain an incoming message");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * =====================================================
          * STEP 2: GET MESSAGE
          * =====================================================
          */

         JsonNode message =
                 messages.get(0);

         String fromNumber =
                 message.path("from").asText();

         String messageText =
                 message.path("text")
                        .path("body")
                        .asText();

         System.out.println(
                 "Patient WhatsApp Number = "
                 + fromNumber);

         System.out.println(
                 "Patient Message = "
                 + messageText);

         /*
          * =====================================================
          * STEP 3: GET DOCTOR WHATSAPP NUMBER
          * =====================================================
          */

         String doctorNumber =
                 value.path("metadata")
                      .path("display_phone_number")
                      .asText();

         System.out.println(
                 "Doctor WhatsApp Number from Meta = "
                 + doctorNumber);

         /*
          * =====================================================
          * STEP 4: NORMALIZE DOCTOR NUMBER
          *
          * Example:
          *
          * Meta:
          * +91 91654 10555
          *
          * DB:
          * 9165410555
          *
          * =====================================================
          */

         doctorNumber =
                 doctorNumber.replaceAll("\\D", "");

         if (doctorNumber.startsWith("91")
                 && doctorNumber.length() > 10) {

             doctorNumber =
                     doctorNumber.substring(2);
         }

         System.out.println(
                 "Normalized Doctor WhatsApp Number = "
                 + doctorNumber);

         /*
          * =====================================================
          * STEP 5: FIND DOCTOR FROM doctors_details
          * =====================================================
          */

         Optional<Doctor> doctorOptional =
                 doctorRepo.findByWhatsappNumber(
                         doctorNumber
                 );

         if (doctorOptional.isEmpty()) {

             System.out.println(
                     "Doctor not found for WhatsApp number = "
                     + doctorNumber);

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         Doctor doctor =
                 doctorOptional.get();

         System.out.println(
                 "Doctor found = "
                 + doctor.getFullName());

         System.out.println(
                 "Doctor ID = "
                 + doctor.getId());

         /*
          * =====================================================
          * STEP 6: VALIDATE WHATSAPP CONFIGURATION
          * =====================================================
          */

         if (!doctor.isWhatsappActivated()) {

             System.out.println(
                     "WhatsApp is not activated for doctor");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         if (doctor.getWhatsappPhoneNumberId() == null
                 || doctor.getWhatsappPhoneNumberId().isBlank()) {

             System.out.println(
                     "WhatsApp Phone Number ID is missing");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         if (doctor.getWhatsappAccessToken() == null
                 || doctor.getWhatsappAccessToken().isBlank()) {

             System.out.println(
                     "WhatsApp Access Token is missing");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * =====================================================
          * STEP 7: CALL GEMINI
          * =====================================================
          */

         System.out.println(
                 "Calling AI service...");

         String aiResponse =
                 aiService.generateResponse(
                         messageText,
                         doctor
                 );

         System.out.println(
                 "AI Response = "
                 + aiResponse);

         /*
          * =====================================================
          * STEP 8: SEND RESPONSE TO PATIENT
          *
          * IMPORTANT:
          *
          * fromNumber = patient number
          *
          * doctor = contains:
          * - whatsappAccessToken
          * - whatsappPhoneNumberId
          * =====================================================
          */

         whatsAppService.sendMessage(
                 doctor,
                 fromNumber,
                 aiResponse
         );

         System.out.println(
                 "Response sent successfully to patient");

     } catch (Exception e) {

         System.err.println(
                 "Error processing WhatsApp webhook: "
                 + e.getMessage());

         e.printStackTrace();
     }

     /*
      * Always return 200 to Meta
      * after receiving the webhook.
      */
     return ResponseEntity.ok("EVENT_RECEIVED");
 }
}