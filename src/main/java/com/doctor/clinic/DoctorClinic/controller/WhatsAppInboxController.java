package com.doctor.clinic.DoctorClinic.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.service.WhatsAppConversationService;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppInboxController {

    private final WhatsAppConversationService conversationService;

    public WhatsAppInboxController(WhatsAppConversationService conversationService) {
        this.conversationService = conversationService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> getConversations() {
        return ResponseEntity.ok(conversationService.getConversationsForCurrentOrganization());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.getConversation(conversationId, currentOrganizationId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.getMessagesForConversation(conversationId, currentOrganizationId()));
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable Long conversationId,
                                                          @RequestBody Map<String, Object> payload) {
        String content = payload.getOrDefault("content", "").toString();
        String messageType = payload.getOrDefault("messageType", "TEXT").toString();
        return ResponseEntity.ok(conversationService.sendMessage(conversationId, currentOrganizationId(), content, messageType));
    }

    @PostMapping("/conversations/{conversationId}/takeover")
    public ResponseEntity<Map<String, Object>> takeover(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.toggleTakeover(conversationId, currentOrganizationId(), true));
    }

    @PostMapping("/conversations/{conversationId}/resume-ai")
    public ResponseEntity<Map<String, Object>> resumeAi(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.toggleTakeover(conversationId, currentOrganizationId(), false));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.markConversationRead(conversationId, currentOrganizationId()));
    }

    @PostMapping("/conversations/{conversationId}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable Long conversationId) {
        return ResponseEntity.ok(conversationService.closeConversation(conversationId, currentOrganizationId()));
    }

    private Long currentOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object details = authentication != null ? authentication.getDetails() : null;
        if (details instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Organization context is missing");
    }
}
