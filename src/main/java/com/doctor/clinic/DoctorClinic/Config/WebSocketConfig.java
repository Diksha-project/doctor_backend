package com.doctor.clinic.DoctorClinic.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.doctor.clinic.DoctorClinic.security.JwtUtil;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthInterceptor());
    }

    @Bean
    public ChannelInterceptor stompAuthInterceptor() {
        return new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null) {
                    return message;
                }

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new AccessDeniedException("Missing JWT for WebSocket connection");
                    }

                    String token = authHeader.substring(7).trim();
                    if (!jwtUtil.isTokenValid(token)) {
                        throw new AccessDeniedException("Invalid JWT for WebSocket connection");
                    }

                    String email = jwtUtil.extractEmail(token);
                    String role = jwtUtil.extractRole(token);
                    Long organizationId = jwtUtil.extractOrganizationId(token);
                    UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                            email,
                            null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + role))
                    );
                    authentication.setDetails(organizationId);
                    accessor.setUser(authentication);
                    if (accessor.getSessionAttributes() != null) {
                        accessor.getSessionAttributes().put("organizationId", organizationId);
                    }
                    return message;
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (!(accessor.getUser() instanceof Authentication authentication)
                            || !(authentication.getDetails() instanceof Number organizationId)) {
                        throw new AccessDeniedException("Authentication required for chat subscriptions");
                    }

                    String destination = accessor.getDestination();
                    if ("/user/queue/whatsapp".equals(destination)) {
                        return message;
                    }

                    if (destination == null || !destination.startsWith("/topic/organizations/")) {
                        throw new AccessDeniedException("Invalid topic destination");
                    }

                    String[] parts = destination.split("/");
                    if (parts.length < 6 || !"whatsapp".equals(parts[4])) {
                        throw new AccessDeniedException("Invalid WhatsApp topic destination");
                    }

                    try {
                        Long requestedOrganizationId = Long.parseLong(parts[3]);
                        if (!requestedOrganizationId.equals(organizationId.longValue())) {
                            throw new AccessDeniedException("Organization mismatch for WebSocket subscription");
                        }
                    } catch (NumberFormatException ex) {
                        throw new AccessDeniedException("Invalid organization id in topic destination");
                    }
                }

                return message;
            }
        };
    }
}
