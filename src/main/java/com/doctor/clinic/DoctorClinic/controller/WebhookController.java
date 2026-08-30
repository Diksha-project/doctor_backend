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

 /*
  * ============================================================
  * META WEBHOOK VERIFICATION
  * ============================================================
  *
  * Meta calls:
  *
  * GET /webhook/whatsapp
  *
  * Meta sends:
  *
  * hub.mode
  * hub.verify_token
  * hub.challenge
  */
 @GetMapping("/whatsapp")
 public String verifyWebhook(

         @RequestParam("hub.mode") String mode,

         @RequestParam("hub.verify_token") String token,

         @RequestParam("hub.challenge") String challenge) {

     System.out.println("=================================");
     System.out.println("WHATSAPP WEBHOOK VERIFICATION");
     System.out.println("=================================");

     System.out.println("Mode = " + mode);
     System.out.println("Verify Token Received = "
             + (token != null && !token.isBlank()));

     if ("subscribe".equals(mode)
             && VERIFY_TOKEN.equals(token)) {

         System.out.println("Webhook verification SUCCESS");

         return challenge;
     }

     System.out.println("Webhook verification FAILED");

     return "Verification failed";
 }

 /*
  * ============================================================
  * WHATSAPP INCOMING MESSAGE
  * ============================================================
  *
  * Meta calls:
  *
  * POST /webhook/whatsapp
  *
  * whenever a WhatsApp event/message is received.
  */
 @PostMapping("/whatsapp")
 public ResponseEntity<String> handleIncomingMessages(
         @RequestBody String payload) {

     System.out.println();
     System.out.println("=================================");
     System.out.println("WHATSAPP WEBHOOK RECEIVED");
     System.out.println("=================================");

     System.out.println("Webhook payload = " + payload);

     try {

         /*
          * ====================================================
          * STEP 1: PARSE JSON
          * ====================================================
          */

         JsonNode json = objectMapper.readTree(payload);

         JsonNode entry = json.path("entry").get(0);

         if (entry == null || entry.isMissingNode()) {

             System.out.println("Entry not found");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         JsonNode change = entry.path("changes").get(0);

         if (change == null || change.isMissingNode()) {

             System.out.println("Change not found");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         JsonNode value = change.path("value");

         /*
          * ====================================================
          * STEP 2: CHECK FOR MESSAGE
          * ====================================================
          *
          * Meta also sends webhook events for:
          *
          * - message status
          * - delivered
          * - read
          * - sent
          *
          * Those may NOT contain "messages".
          */

         JsonNode messages = value.path("messages");

         if (!messages.isArray()
                 || messages.isEmpty()) {

             System.out.println(
                     "Webhook received but no incoming message found");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * ====================================================
          * STEP 3: GET MESSAGE
          * ====================================================
          */

         JsonNode message = messages.get(0);

         /*
          * Patient WhatsApp number
          *
          * Example:
          *
          * 919999999999
          */
         String fromNumber =
                 message.path("from").asText(null);

         /*
          * Patient's message
          *
          * Example:
          *
          * Hello Doctor
          */
         String messageText =
                 message.path("text")
                        .path("body")
                        .asText(null);

         System.out.println(
                 "Patient WhatsApp Number = "
                 + fromNumber);

         System.out.println(
                 "Patient Message = "
                 + messageText);

         /*
          * ====================================================
          * STEP 4: VALIDATE MESSAGE
          * ====================================================
          */

         if (fromNumber == null
                 || fromNumber.isBlank()) {

             System.out.println(
                     "Patient WhatsApp number missing");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         if (messageText == null
                 || messageText.isBlank()) {

             System.out.println(
                     "Message text missing");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * ====================================================
          * STEP 5: GET DOCTOR WHATSAPP NUMBER
          * ====================================================
          *
          * This comes from:
          *
          * value.metadata.display_phone_number
          *
          * This is the WhatsApp number that RECEIVED
          * the patient's message.
          */

         String doctorNumber =
                 value.path("metadata")
                      .path("display_phone_number")
                      .asText(null);

         System.out.println(
                 "Doctor WhatsApp Number from Meta = "
                 + doctorNumber);

         if (doctorNumber == null
                 || doctorNumber.isBlank()) {

             System.out.println(
                     "Doctor WhatsApp number missing from Meta");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * ====================================================
          * STEP 6: NORMALIZE DOCTOR NUMBER
          * ====================================================
          *
          * IMPORTANT:
          *
          * We are keeping the number format compatible
          * with your current database.
          *
          * Example:
          *
          * DB:
          * 9165410555
          *
          * Postman:
          * 9165410555
          *
          * Therefore we do NOT remove 91 here.
          *
          * We only remove formatting characters.
          */

         doctorNumber =
                 doctorNumber.replaceAll("\\D", "");

         System.out.println(
                 "Normalized Doctor WhatsApp Number = "
                 + doctorNumber);

         /*
          * ====================================================
          * STEP 7: FIND DOCTOR
          * ====================================================
          *
          * This queries:
          *
          * doctors_details
          *
          * using:
          *
          * whatsapp_number
          */

         Optional<Doctor> doctorOptional =
                 doctorRepo.findByWhatsappNumber(
                         doctorNumber);

         if (doctorOptional.isEmpty()) {

             System.out.println(
                     "=================================");

             System.out.println(
                     "DOCTOR NOT FOUND");

             System.out.println(
                     "WhatsApp Number = "
                     + doctorNumber);

             System.out.println(
                     "=================================");

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         Doctor doctor =
                 doctorOptional.get();

         System.out.println(
                 "=================================");

         System.out.println(
                 "DOCTOR FOUND");

         System.out.println(
                 "Doctor ID = "
                 + doctor.getId());

         System.out.println(
                 "Doctor Name = "
                 + doctor.getFullName());

         System.out.println(
                 "Doctor WhatsApp Number = "
                 + doctor.getWhatsappNumber());

         System.out.println(
                 "=================================");

         /*
          * ====================================================
          * STEP 8: CHECK WHATSAPP ACTIVATION
          * ====================================================
          */

         if (!doctor.isWhatsappActivated()) {

             System.out.println(
                     "WhatsApp is NOT activated for doctor "
                     + doctor.getId());

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * ====================================================
          * STEP 9: CHECK WHATSAPP PHONE NUMBER ID
          * ====================================================
          */

         if (doctor.getWhatsappPhoneNumberId() == null
                 || doctor.getWhatsappPhoneNumberId().isBlank()) {

             System.out.println(
                     "WhatsApp Phone Number ID is missing "
                     + "for doctor "
                     + doctor.getId());

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * ====================================================
          * STEP 10: CHECK WHATSAPP ACCESS TOKEN
          * ====================================================
          */

         if (doctor.getWhatsappAccessToken() == null
                 || doctor.getWhatsappAccessToken().isBlank()) {

             System.out.println(
                     "WhatsApp Access Token is missing "
                     + "for doctor "
                     + doctor.getId());

             return ResponseEntity.ok("EVENT_RECEIVED");
         }

         /*
          * ====================================================
          * STEP 11: CALL GEMINI
          * ====================================================
          */

         System.out.println(
                 "=================================");

         System.out.println(
                 "CALLING AI SERVICE");

         System.out.println(
                 "=================================");

         String aiResponse =
                 aiService.generateResponse(
                         messageText,
                         doctor);

         System.out.println(
                 "AI Response = "
                 + aiResponse);

         /*
          * ====================================================
          * STEP 12: SEND RESPONSE TO PATIENT
          * ====================================================
          *
          * IMPORTANT:
          *
          * doctor
          *     ↓
          * contains doctor's:
          *
          * - WhatsApp Access Token
          * - WhatsApp Phone Number ID
          *
          * fromNumber
          *     ↓
          * is the PATIENT number
          *
          * Therefore the response goes:
          *
          * Doctor WhatsApp account
          *             ↓
          *       Patient number
          */

         System.out.println(
                 "=================================");

         System.out.println(
                 "SENDING WHATSAPP RESPONSE");

         System.out.println(
                 "To Patient = "
                 + fromNumber);

         System.out.println(
                 "=================================");

         whatsAppService.sendMessage(
                 doctor,
                 fromNumber,
                 aiResponse);

         System.out.println(
                 "=================================");

         System.out.println(
                 "RESPONSE SENT SUCCESSFULLY");

         System.out.println(
                 "=================================");

     } catch (Exception e) {

         System.err.println(
                 "=================================");

         System.err.println(
                 "ERROR PROCESSING WHATSAPP WEBHOOK");

         System.err.println(
                 "=================================");

         System.err.println(
                 "Error = "
                 + e.getMessage());

         e.printStackTrace();
     }

     /*
      * ========================================================
      * IMPORTANT
      * ========================================================
      *
      * Always return HTTP 200 after receiving the webhook.
      *
      * This tells Meta that our server received the event.
      */

     return ResponseEntity.ok("EVENT_RECEIVED");
 }
}