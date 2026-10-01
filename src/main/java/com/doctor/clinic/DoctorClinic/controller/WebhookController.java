package com.doctor.clinic.DoctorClinic.controller;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.AIServices.GeminiServiceLatest;
import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.model.Intent;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.ProcessedWhatsappMessageRepo;
import com.doctor.clinic.DoctorClinic.entity.ProcessedWhatsappMessage;
import com.doctor.clinic.DoctorClinic.serviceImpl.IntentDetector;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@RestController
@RequestMapping("/webhook")
public class WebhookController {

    @Value("${facebook.webhook.verify-token}")
    private String verifyToken;

    private final DoctorRepo doctorRepo;
    private final GeminiServiceLatest aiService;
    private final WhatsappServiceImpl whatsAppService;
    private final ObjectMapper objectMapper;
    private final IntentDetector intentDetector;
    private final ProcessedWhatsappMessageRepo processedMessageRepo;

    public WebhookController(
            DoctorRepo doctorRepo,
            GeminiServiceLatest aiService,
            WhatsappServiceImpl whatsAppService,
            ObjectMapper objectMapper,
            IntentDetector intentDetector,
            ProcessedWhatsappMessageRepo processedMessageRepo) {

        this.doctorRepo = doctorRepo;
        this.aiService = aiService;
        this.whatsAppService = whatsAppService;
        this.objectMapper = objectMapper;
        this.intentDetector = intentDetector;
        this.processedMessageRepo = processedMessageRepo;
    }

    /*
     * ============================================================
     * HEALTH CHECK
     * ============================================================
     *
     * Used to check whether Render application is alive.
     *
     * GET /webhook/health
     */
    @GetMapping("/health")
    public String health() {

        System.out.println("Health check received");

        return "OK";
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

        System.out.println(
                "Verify Token Received = "
                        + (token != null && !token.isBlank()));

        if ("subscribe".equals(mode)
                && verifyToken.equals(token)) {

            System.out.println(
                    "Webhook verification SUCCESS");

            return challenge;
        }

        System.out.println(
                "Webhook verification FAILED");

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

        System.out.println(
                "Webhook payload = " + payload);

        try {

            /*
             * ====================================================
             * STEP 1: PARSE JSON
             * ====================================================
             */

            JsonNode json =
                    objectMapper.readTree(payload);

            JsonNode entry =
                    json.path("entry").get(0);

            if (entry == null
                    || entry.isMissingNode()) {

                System.out.println(
                        "Entry not found");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            JsonNode change =
                    entry.path("changes").get(0);

            if (change == null
                    || change.isMissingNode()) {

                System.out.println(
                        "Change not found");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            JsonNode value =
                    change.path("value");

            /*
             * ====================================================
             * STEP 2: CHECK MESSAGE
             * ====================================================
             */

            JsonNode messages =
                    value.path("messages");

            if (!messages.isArray()
                    || messages.isEmpty()) {

                System.out.println(
                        "Webhook received but no incoming message found");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            /*
             * ====================================================
             * STEP 3: GET MESSAGE
             * ====================================================
             */

            JsonNode message =
                    messages.get(0);

            String messageId = message.path("id").asText(null);
            if (messageId != null && !messageId.isBlank()) {
                try {
                    processedMessageRepo.saveAndFlush(
                            new ProcessedWhatsappMessage(messageId));
                } catch (DataIntegrityViolationException duplicate) {
                    System.out.println("Ignoring duplicate WhatsApp message ID = " + messageId);
                    return ResponseEntity.ok("EVENT_RECEIVED");
                }
            }

            String messageType =
                    message.path("type").asText();

            System.out.println(
                    "Message Type = " + messageType);

            /*
             * We currently process only TEXT messages.
             *
             * Image/audio/video/document messages are ignored.
             */
            if (!"text".equals(messageType)) {

                System.out.println(
                        "Ignoring non-text WhatsApp message: "
                                + messageType);

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            /*
             * ====================================================
             * STEP 4: GET PATIENT NUMBER + MESSAGE
             * ====================================================
             */

            String fromNumber =
                    message.path("from").asText(null);

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
             * STEP 5: VALIDATE MESSAGE
             * ====================================================
             */

            if (fromNumber == null
                    || fromNumber.isBlank()) {

                System.out.println(
                        "Patient WhatsApp number missing");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            if (messageText == null
                    || messageText.isBlank()) {

                System.out.println(
                        "Message text missing");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            /*
             * ====================================================
             * STEP 6: GET DOCTOR WHATSAPP NUMBER
             * ====================================================
             *
             * This is the number on which patient sent message.
             *
             * Example:
             *
             * 919165410555
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

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            /*
             * ====================================================
             * STEP 7: NORMALIZE DOCTOR NUMBER
             * ====================================================
             */

            doctorNumber =
                    doctorNumber.replaceAll("\\D", "");

            System.out.println(
                    "Normalized Doctor WhatsApp Number = "
                            + doctorNumber);

            /*
             * ====================================================
             * STEP 8: FIND DOCTOR
             * ====================================================
             */

            Optional<Doctor> doctorOptional =
                    doctorRepo.findByWhatsappNumber(
                            doctorNumber);

            if (doctorOptional.isEmpty()) {

                System.out.println(
                        "DOCTOR NOT FOUND");

                System.out.println(
                        "WhatsApp Number = "
                                + doctorNumber);

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            Doctor doctor =
                    doctorOptional.get();


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

            /*
             * ====================================================
             * STEP 9: CHECK WHATSAPP ACTIVATION
             * ====================================================
             */

            if (!doctor.isWhatsappActivated()) {

                System.out.println(
                        "WhatsApp is NOT activated for doctor "
                                + doctor.getId());

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            /*
             * ====================================================
             * STEP 10: CHECK PHONE NUMBER ID
             * ====================================================
             */

            if (doctor.getWhatsappPhoneNumberId() == null
                    || doctor.getWhatsappPhoneNumberId().isBlank()) {

                System.out.println(
                        "WhatsApp Phone Number ID is missing");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }

            /*
             * ====================================================
             * STEP 11: CHECK ACCESS TOKEN
             * ====================================================
             *
             * IMPORTANT:
             *
             * Access token comes from DATABASE.
             *
             * We DO NOT use the webhook verify token here.
             */

            if (doctor.getWhatsappAccessToken() == null
                    || doctor.getWhatsappAccessToken().isBlank()) {

                System.out.println(
                        "WhatsApp Access Token is missing");

                return ResponseEntity.ok(
                        "EVENT_RECEIVED");
            }
            
         // ====================================================
         // STEP 12: DETECT INTENT
         // ====================================================


         System.out.println(
                 "DETECTING MESSAGE INTENT Patient Message = " + messageText
         );

         Intent intent =
                 intentDetector.detect(messageText);

         System.out.println(
                 "Detected Intent = " + intent
         );


         // ====================================================
         // STEP 13: HANDLE INTENT
         // ====================================================

         String response;

         if (intent == Intent.GREETING) {

             System.out.println(
                     "GREETING detected - Gemini NOT called"
             );

             response =
                     "Hello! I am "
                     + doctor.getFullName()
                     + "'s virtual assistant. "
                     + "How can I help you?";

         } else if (intent == Intent.THANKS) {

             System.out.println(
                     "THANKS detected - Gemini NOT called"
             );

             response =
                     "You're welcome! Is there anything else I can help you with?";

         } else if (intent == Intent.GOODBYE) {

             System.out.println(
                     "GOODBYE detected - Gemini NOT called"
             );

             response =
                     "Thank you for contacting the clinic. Have a great day!";

         } else {

             System.out.println(
                     "UNKNOWN intent - Calling Gemini"
             );

             response =
                     aiService.generateResponse(
                             messageText,
                             doctor);
         }

//            /*
//             * ====================================================
//             * STEP 12: SEND MESSAGE TO GEMINI
//             * ====================================================
//             *
//             * Patient message:
//             *
//             * "What are your consultation timings?"
//             *
//             * goes to Gemini.
//             */
//
//            System.out.println(
//                    "=================================");
//
//            System.out.println(
//                    "CALLING AI SERVICE");
//
//            System.out.println(
//                    "Patient Message = "
//                            + messageText);
//
//            System.out.println(
//                    "=================================");
//
//            String aiResponse =
//                    aiService.generateResponse(
//                            messageText,
//                            doctor);
//
//            System.out.println(
//                    "AI Response = "
//                            + aiResponse);
//
//            /*
//             * ====================================================
//             * STEP 13: SEND AI RESPONSE TO PATIENT
//             * ====================================================
//             *
//             * doctor contains:
//             *
//             * - WhatsApp access token
//             * - WhatsApp phone number ID
//             *
//             * fromNumber contains:
//             *
//             * - patient's WhatsApp number
//             */
//
//            System.out.println(
//                    "=================================");
//
//            System.out.println(
//                    "SENDING WHATSAPP RESPONSE");
//
//            System.out.println(
//                    "To Patient = "
//                            + fromNumber);
//
//            System.out.println(
//                    "=================================");
//
//            whatsAppService.sendMessage(
//                    doctor,
//                    fromNumber,
//                    aiResponse);
//
//            System.out.println(
//                    "=================================");
//
//            System.out.println(
//                    "RESPONSE SENT SUCCESSFULLY");
//
//            System.out.println(
//                    "=================================");
//
//        } catch (Exception e) {
//
//            System.err.println(
//                    "=================================");
//
//            System.err.println(
//                    "ERROR PROCESSING WHATSAPP WEBHOOK");
//
//            System.err.println(
//                    "=================================");
//
//            System.err.println(
//                    "Error = "
//                            + e.getMessage());
//
//            e.printStackTrace();
//        }

        /*
         * Always return HTTP 200 to Meta.
         */
        
      // ====================================================
      // STEP 14: SEND RESPONSE TO PATIENT
      // ====================================================


      System.out.println(
              "SENDING WHATSAPP RESPONSE To Patient = " + fromNumber
      );

 

      whatsAppService.sendMessage(
              doctor,
              fromNumber,
              response
      );

      System.out.println(
              "RESPONSE SENT SUCCESSFULLY"
      );


      } catch (Exception e) {

         
          System.err.println(
                  "ERROR PROCESSING WHATSAPP WEBHOOK" + e.getMessage()
          );

          e.printStackTrace();
      }

      // ====================================================
      // ALWAYS RETURN HTTP 200 TO META
      // ====================================================

      return ResponseEntity.ok(
              "EVENT_RECEIVED"
      );
      
    }
    }
