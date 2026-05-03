package com.doctor.clinic.DoctorClinic.security;



import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;



import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {
 
	
	
	    
	    private final JwtUtil jwtUtil;
	    
	  
        
	    
	    @Override
	    protected void doFilterInternal(HttpServletRequest request,
	                                    HttpServletResponse response,
	                                    FilterChain filterChain)
	            throws ServletException, IOException {
	    	
	    	
	        
	        String path = request.getRequestURI();
	        
	        if (path.equals("/api/auth/login") || path.equals("/org/register") || path.equals("/api/auth/register")) {
	              log.info("Skipping authentication for public path: {}", path);
	              filterChain.doFilter(request, response);
	              return;
	          }
	        
	        // Skip authentication for login endpoint
	        if (path.equals("/api/auth/login")) {
	            filterChain.doFilter(request, response);
	            return;
	        }
	        
	        String authHeader = request.getHeader("Authorization");
	        
	        if (authHeader != null && authHeader.startsWith("Bearer ")) {
	            String token = authHeader.substring(7);
	            
	            try {
	                if (jwtUtil.isTokenValid(token)) {
	                    String email = jwtUtil.extractEmail(token);
	                    String role = jwtUtil.extractRole(token);
	                    Long organizationId = jwtUtil.extractOrganizationId(token);
	                    
	                    log.info("Valid token for user: {}, role: {}", email, role);
	                    
	                    // ========== CRITICAL: Set authentication in SecurityContext ==========
	                    UsernamePasswordAuthenticationToken authentication = 
	                        new UsernamePasswordAuthenticationToken(
	                            email, 
	                            null, 
	                            Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + role))
	                        );
	                    
	                    // Store additional details if needed
	                    authentication.setDetails(organizationId);
	                    
	                    SecurityContextHolder.getContext().setAuthentication(authentication);
	                    
	                    // Also store in request attributes for convenience
	                    request.setAttribute("email", email);
	                    request.setAttribute("role", role);
	                    request.setAttribute("organizationId", organizationId);
	                } else {
	                    log.warn("Invalid token");
	                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	                    response.getWriter().write("Invalid or expired token");
	                    return;
	                }
	            } catch (Exception e) {
	                log.error("Token validation error: {}", e.getMessage());
	                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	                response.getWriter().write("Invalid or expired token");
	                return;
	            }
	        } else {
	            log.warn("No Authorization header found");
	            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
	            response.getWriter().write("Missing Authorization header");
	            return;
	        }
	        
	        filterChain.doFilter(request, response);
	    }
	}