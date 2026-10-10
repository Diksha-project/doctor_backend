package com.doctor.clinic.DoctorClinic.security;

import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.doctor.clinic.DoctorClinic.entity.AppUser;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurrentUserService {
    private final AppUserRepo appUserRepo;

    public CurrentUser getRequiredCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return currentUser;
    }

    public CurrentUser loadByEmail(String email) {
        AppUser user = appUserRepo.findDetailedByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is not active"));
        if (!user.isActive()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User is deactivated");
        }
        return toCurrentUser(user);
    }

    public CurrentUser toCurrentUser(AppUser user) {
        Set<String> roles = appUserRepo.findActiveRoleCodesByUserId(user.getId());
        Set<String> permissions = appUserRepo.findPermissionCodesByUserId(user.getId());

        if (roles.contains("SUPER_ADMIN")) {
            permissions = PermissionCode.allCodes();
        }

        Set<ResourceScope> scopes = appUserRepo.findActiveScopeNamesByUserId(user.getId()).stream()
                .map(ResourceScope::valueOf)
                .collect(Collectors.toSet());

        return new CurrentUser(
                user.getId(),
                user.getOrganization().getId(),
                user.getDoctor() == null ? null : user.getDoctor().getId(),
                user.getEmail(),
                user.getFullName(),
                roles,
                permissions,
                scopes
        );
    }
}
