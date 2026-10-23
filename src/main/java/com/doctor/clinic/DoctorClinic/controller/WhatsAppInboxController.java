package com.doctor.clinic.DoctorClinic.controller;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ContentDisposition;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.service.WhatsAppConversationService;
import com.doctor.clinic.DoctorClinic.request.StartWhatsAppConversationRequest;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/whatsapp")
public class WhatsAppInboxController {

    private final WhatsAppConversationService conversationService;
    private final AuthorizationService authorizationService;

    public WhatsAppInboxController(WhatsAppConversationService conversationService, AuthorizationService authorizationService) {
        this.conversationService = conversationService;
        this.authorizationService = authorizationService;
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<Map<String, Object>>> getConversations() {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_VIEW);
        return ResponseEntity.ok(conversationService.getConversationsForCurrentOrganization());
    }

    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<Map<String, Object>> getConversation(@PathVariable Long conversationId) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_VIEW);
        return ResponseEntity.ok(conversationService.getConversation(conversationId, currentOrganizationId()));
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<Map<String, Object>>> getMessages(@PathVariable Long conversationId) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_VIEW);
        return ResponseEntity.ok(conversationService.getMessagesForConversation(conversationId, currentOrganizationId()));
    }

    @GetMapping("/conversations/{conversationId}/messages/{messageId}/attachment")
    public ResponseEntity<byte[]> getMedicalReport(@PathVariable Long conversationId,
                                                   @PathVariable Long messageId) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_VIEW);
        var file = conversationService.downloadMedicalReport(conversationId, messageId, currentOrganizationId());
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(file.contentType());
        } catch (Exception ex) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }
        String fileName = file.fileName() == null ? "medical-report" : file.fileName().replaceAll("[\\r\\n\\\"]", "_");
        return ResponseEntity.ok().contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(fileName).build().toString())
                .body(file.bytes());
    }

    @PostMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(@PathVariable Long conversationId,
                                                          @RequestBody Map<String, Object> payload) {
        String content = payload.getOrDefault("content", "").toString();
        String messageType = payload.getOrDefault("messageType", "TEXT").toString();
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_REPLY);
        return ResponseEntity.ok(conversationService.sendMessage(conversationId, currentOrganizationId(), content, messageType));
    }

    @PostMapping("/conversations")
    public ResponseEntity<Map<String, Object>> startConversation(
            @Valid @RequestBody StartWhatsAppConversationRequest request) {
        authorizationService.requireDoctorPermission(
                PermissionCode.WHATSAPP_INBOX_REPLY, currentOrganizationId(), request.getUserId());
        return ResponseEntity.ok(conversationService.startConversation(
                currentOrganizationId(), request.getPatientId(), request.getUserId()));
    }

    @PostMapping(value = "/conversations/{conversationId}/attachments", consumes = "multipart/form-data")
    public ResponseEntity<Map<String, Object>> sendAttachment(@PathVariable Long conversationId,
            @org.springframework.web.bind.annotation.RequestParam("file") org.springframework.web.multipart.MultipartFile file,
            @org.springframework.web.bind.annotation.RequestParam(value = "caption", required = false) String caption) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_REPLY);
        return ResponseEntity.ok(conversationService.sendAttachment(conversationId, currentOrganizationId(), file, caption));
    }

    @PostMapping("/conversations/{conversationId}/takeover")
    public ResponseEntity<Map<String, Object>> takeover(@PathVariable Long conversationId) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_TAKEOVER);
        return ResponseEntity.ok(conversationService.toggleTakeover(conversationId, currentOrganizationId(), true));
    }

    @PostMapping("/conversations/{conversationId}/resume-ai")
    public ResponseEntity<Map<String, Object>> resumeAi(@PathVariable Long conversationId) {
        authorizationService.requirePermission(PermissionCode.AI_ASSISTANT_CONFIGURE);
        return ResponseEntity.ok(conversationService.toggleTakeover(conversationId, currentOrganizationId(), false));
    }

    @PostMapping("/conversations/{conversationId}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable Long conversationId) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_VIEW);
        return ResponseEntity.ok(conversationService.markConversationRead(conversationId, currentOrganizationId()));
    }

    @PostMapping("/conversations/{conversationId}/close")
    public ResponseEntity<Map<String, Object>> close(@PathVariable Long conversationId) {
        authorizationService.requirePermission(PermissionCode.WHATSAPP_INBOX_CLOSE);
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
