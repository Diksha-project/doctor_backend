package com.doctor.clinic.DoctorClinic.security;

import java.util.Collections;
import java.util.Set;

import com.doctor.clinic.DoctorClinic.model.ResourceScope;

public record CurrentUser(
        Long userId,
        Long organizationId,
        String email,
        String name,
        Set<String> roles,
        Set<String> permissions,
        Set<ResourceScope> scopes
) {
    public CurrentUser {
        roles = roles == null ? Set.of() : Collections.unmodifiableSet(roles);
        permissions = permissions == null ? Set.of() : Collections.unmodifiableSet(permissions);
        scopes = scopes == null ? Set.of() : Collections.unmodifiableSet(scopes);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasPermission(PermissionCode permission) {
        return permissions.contains(permission.code());
    }

    public boolean hasScope(ResourceScope scope) {
        return scopes.contains(scope);
    }

    public boolean isSuperAdmin() {
        return roles.stream().anyMatch(role -> "SUPER_ADMIN".equalsIgnoreCase(role.replace("ROLE_", "")));
    }

    public boolean isPlatformAdmin() {
        return roles.stream().anyMatch(role -> "PLATFORM_ADMIN".equalsIgnoreCase(role.replace("ROLE_", "")));
    }

    public boolean isDoctor() {
        return roles.stream().anyMatch(role -> "DOCTOR".equalsIgnoreCase(role.replace("ROLE_", "")));
    }
}
