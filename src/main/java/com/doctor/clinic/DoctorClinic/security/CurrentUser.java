package com.doctor.clinic.DoctorClinic.security;

import java.util.Collections;
import java.util.Set;

import com.doctor.clinic.DoctorClinic.model.ResourceScope;

public record CurrentUser(
        Long userId,
        Long organizationId,
        Long doctorId,
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

    public boolean hasScope(ResourceScope scope) {
        return scopes.contains(scope);
    }
}
