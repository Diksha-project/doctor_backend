package com.doctor.clinic.DoctorClinic.security;



import java.time.LocalDateTime;

import com.doctor.clinic.DoctorClinic.entity.AppUser;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.request.LoginRequest;
import com.doctor.clinic.DoctorClinic.response.LoginResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {
    
    private final OrganizationRepo organizationRepo;
    private final AppUserRepo appUserRepo;
    private final JwtUtil jwtUtil;
    private final CurrentUserService currentUserService;
    private final PasswordEncoder passwordEncoder;
    
    public LoginResponse login(LoginRequest request) {

        AppUser user = appUserRepo.findDetailedByEmailIgnoreCase(request.getEmail())
                .orElseGet(() -> createLegacyOrganizationOwnerUser(request.getEmail()));

        if (!passwordMatches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("Invalid email or password");
        }

        if (!user.isActive()) {
            throw new RuntimeException("Account is deactivated. Please contact support.");
        }

        Organization organization = user.getOrganization();
        if (organization == null || !Boolean.TRUE.equals(organization.getIsActive())) {
            throw new RuntimeException("Account is deactivated. Please contact support.");
        }

        if (!isBcryptHash(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        user.setLastLoginAt(LocalDateTime.now());
        appUserRepo.save(user);

        organization.setLastLoginAt(LocalDateTime.now());
        organizationRepo.save(organization);

        CurrentUser currentUser = currentUserService.toCurrentUser(
            appUserRepo.findDetailedByEmailIgnoreCase(user.getEmail()).orElse(user)
        );
        String primaryRole = currentUser.roles().stream().sorted().findFirst().orElse("USER");
        String token = jwtUtil.generateToken(
            user.getEmail(),
            user.getId(),
            primaryRole,
            organization.getId()
        );

        return LoginResponse.builder()
            .token(token)
            .email(user.getEmail())
            .name(user.getFullName())
            .role(primaryRole)
            .roles(currentUser.roles())
            .permissions(currentUser.permissions())
            .userId(user.getId())
            .doctorId(user.getDoctor() == null ? null : user.getDoctor().getId())
            .organizationId(organization.getId())
            .organizationName(organization.getOrganizationName())
            .active(user.isActive())
            .message("Login successful")
            .build();
    }

    private AppUser createLegacyOrganizationOwnerUser(String email) {
        Organization organization = organizationRepo.findByOwnerEmail(email)
                .orElseThrow(() -> new RuntimeException("Invalid email or password"));
        throw new RuntimeException("RBAC user migration has not run for organization: " + organization.getId());
    }

    private boolean passwordMatches(String rawPassword, String storedPassword) {
        if (storedPassword == null) {
            return false;
        }
        if (isBcryptHash(storedPassword)) {
            return passwordEncoder.matches(rawPassword, storedPassword);
        }
        return storedPassword.equals(rawPassword);
    }

    private boolean isBcryptHash(String value) {
        return value != null && (value.startsWith("$2a$") || value.startsWith("$2b$") || value.startsWith("$2y$"));
    }
}
