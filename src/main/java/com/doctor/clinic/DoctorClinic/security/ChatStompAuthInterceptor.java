package com.doctor.clinic.DoctorClinic.security;

import java.util.Collections;
import java.util.List;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.doctor.clinic.DoctorClinic.repo.PatientRepo;

@Component
public class ChatStompAuthInterceptor implements ChannelInterceptor {
    private final JwtUtil jwtUtil;
    private final PatientRepo patientRepo;
    private final CurrentUserService currentUserService;

    public ChatStompAuthInterceptor(JwtUtil jwtUtil, PatientRepo patientRepo, CurrentUserService currentUserService) {
        this.jwtUtil = jwtUtil;
        this.patientRepo = patientRepo;
        this.currentUserService = currentUserService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) return message;

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            authenticate(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            authorizeSubscription(accessor);
        } else if (StompCommand.SEND.equals(accessor.getCommand())) {
            throw new AccessDeniedException("Chat messages must be sent through the authenticated REST API");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        List<String> values = accessor.getNativeHeader("Authorization");
        String header = values == null || values.isEmpty() ? null : values.get(0);
        if (header == null || !header.startsWith("Bearer ")) {
            throw new AccessDeniedException("Missing bearer token in STOMP CONNECT frame");
        }
        String token = header.substring(7).trim();
        if (!jwtUtil.isTokenValid(token)) throw new AccessDeniedException("Invalid or expired token");
        CurrentUser currentUser = currentUserService.loadByEmail(jwtUtil.extractEmail(token));
        Long organizationId = currentUser.organizationId();

        var authentication = new UsernamePasswordAuthenticationToken(currentUser, null,
                currentUser.roles().stream().map(role -> new SimpleGrantedAuthority("ROLE_" + role)).toList());
        authentication.setDetails(organizationId);
        accessor.setUser(authentication);
    }

    private void authorizeSubscription(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof UsernamePasswordAuthenticationToken authentication)
                || !(authentication.getDetails() instanceof Number organization)) {
            throw new AccessDeniedException("Authenticated STOMP connection required");
        }
        String destination = accessor.getDestination();
        if (destination == null) throw new AccessDeniedException("Subscription destination is required");
        String orgPrefix = "/topic/org/" + organization.longValue() + "/patients";
        if (destination.equals(orgPrefix)) return;
        if (!destination.startsWith(orgPrefix + "/")) {
            throw new AccessDeniedException("Subscription is outside the authenticated organization");
        }

        String patientIdText = destination.substring((orgPrefix + "/").length());
        if (patientIdText.isBlank() || patientIdText.contains("/")) {
            throw new AccessDeniedException("Invalid patient conversation destination");
        }
        Long patientId;
        try {
            patientId = Long.parseLong(patientIdText);
        } catch (NumberFormatException ex) {
            throw new AccessDeniedException("Invalid patient conversation destination");
        }
        if (patientRepo.findByIdAndOrganizationId(patientId, organization.longValue()).isEmpty()) {
            throw new AccessDeniedException("Patient is outside the authenticated organization");
        }
    }
}
