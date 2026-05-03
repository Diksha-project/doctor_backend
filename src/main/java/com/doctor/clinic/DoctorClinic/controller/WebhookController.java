package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

//WebhookController.java
@RestController
@RequestMapping("/webhook")
public class WebhookController {
 // This value MUST match the one you set in the Meta Dashboard.
 private static final String VERIFY_TOKEN = "EAANNWLz8YiMBRZA0Qu3Gge7IMCZA1leDIaZCzEhx87tzDVLtQu3O0Tx4FJCKdfVdNlDrtQiIRdKguNcP0CVJZAZB3XlAFTOpCcfA0Ad4qkCIVlKPE2TC006uNwzorEUbucZBTIprcXbq6SQjV2Lhong7evNebrhvpQqVmrkam1V2ieCTTbl8vV3UZAZBwfnLIxfOU38yXGmsl41ZAcZCBhW0cuzZAeZBda1nLig22gOQ9iAbPh48mZCnI7nQ2ZAbM96dLOCryw6CcNAZBcxixOjfUkGJhZBJ";

 // This handles Meta's initial verification request.
 @GetMapping("/whatsapp")
 public String verifyWebhook(@RequestParam("hub.mode") String mode,
                             @RequestParam("hub.verify_token") String token,
                             @RequestParam("hub.challenge") String challenge) {
     if ("subscribe".equals(mode) && VERIFY_TOKEN.equals(token)) {
         // Verification is successful if the token matches.
         return challenge;
     }
     return "Verification failed";
 }

 // This handles all incoming WhatsApp messages.
 @PostMapping("/whatsapp")
 public ResponseEntity<String> handleIncomingMessages(@RequestBody String payload) {
     // 1. For now, just log the entire payload so you can inspect it.
     System.out.println("Webhook received: " + payload);
     // 2. We will add the AI parsing logic here in a future step.
     return ResponseEntity.ok("EVENT_RECEIVED");
 }
}
