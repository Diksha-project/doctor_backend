package com.doctor.clinic.DoctorClinic.security;



import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.request.LoginRequest;
import com.doctor.clinic.DoctorClinic.response.LoginResponse;
import com.doctor.clinic.DoctorClinic.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final OrganizationRepo organizationRepo;
    private final JwtUtil jwtUtil;
    
    public LoginResponse login(LoginRequest request) {
        
        // 1. Find organization by email
        Organization organization = organizationRepo.findByOwnerEmail(request.getEmail()).orElseThrow(() -> new RuntimeException("Invalid email or password"));
        
        // 2. Check password (plain text for now, will encrypt later)
        if (!organization.getPasswordHash().equals(request.getPassword())) {
            throw new RuntimeException("Invalid email or password");
        }
        
        // 3. Check if organization is active
        if (!organization.getIsActive()) {
            throw new RuntimeException("Account is deactivated. Please contact support.");
        }
        
        // 4. Update last login time
        organization.setLastLoginAt(java.time.LocalDateTime.now());
        organizationRepo.save(organization);
        
        // 5. Generate JWT token
        String token = jwtUtil.generateToken(
            organization.getOwnerEmail(),
            organization.getRole(),
            organization.getId()
        );
        
        // 6. Build response
        return LoginResponse.builder()
            .token(token)
            .email(organization.getOwnerEmail())
            .role(organization.getRole())
            .organizationId(organization.getId())
            .organizationName(organization.getOrganizationName())
            .message("Login successful")
            .build();
    }
}