package com.doctor.clinic.DoctorClinic.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.doctor.clinic.DoctorClinic.model.ResourceScope;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AuthorizationService {
    private final CurrentUserService currentUserService;

    public CurrentUser requirePermission(String permission) {
        CurrentUser user = currentUserService.getRequiredCurrentUser();
        if (!user.hasPermission(permission)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing permission: " + permission);
        }
        return user;
    }

    public CurrentUser requirePermission(PermissionCode permission) {
        return requirePermission(permission.code());
    }

    public CurrentUser requireOrganizationPermission(String permission, Long organizationId) {
        CurrentUser user = requirePermission(permission);
        if (organizationId == null || !organizationId.equals(user.organizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
        return user;
    }

    public CurrentUser requireOrganizationPermission(PermissionCode permission, Long organizationId) {
        return requireOrganizationPermission(permission.code(), organizationId);
    }

    public CurrentUser requireDoctorPermission(String permission, Long organizationId, Long doctorId) {
        CurrentUser user = requireOrganizationPermission(permission, organizationId);
        if (user.hasScope(ResourceScope.ORGANIZATION)) {
            return user;
        }
        if (user.hasScope(ResourceScope.OWN_DOCTOR)
                && user.doctorId() != null
                && user.doctorId().equals(doctorId)) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }

    public CurrentUser requireOwnOrganization() {
        return currentUserService.getRequiredCurrentUser();
    }

    public CurrentUser requireDoctorPermission(PermissionCode permission, Long organizationId, Long doctorId) {
        return requireDoctorPermission(permission.code(), organizationId, doctorId);
    }

    public CurrentUser requireDoctorProfileAccess(Long organizationId, Long doctorId) {
        CurrentUser user = currentUserService.getRequiredCurrentUser();
        if (organizationId == null || !organizationId.equals(user.organizationId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
        }
        if (user.doctorId() != null && user.doctorId().equals(doctorId)) {
            return user;
        }
        return requireOrganizationDoctorPermission(PermissionCode.DOCTORS_VIEW, organizationId, doctorId);
    }

    public CurrentUser requireOrganizationDoctorPermission(String permission, Long organizationId, Long doctorId) {
        CurrentUser user = requireOrganizationPermission(permission, organizationId);
        if (user.roles().contains("SUPER_ADMIN") || user.hasScope(ResourceScope.ORGANIZATION)) {
            return user;
        }
        if (user.hasScope(ResourceScope.OWN_DOCTOR)
                && user.doctorId() != null
                && user.doctorId().equals(doctorId)) {
            return user;
        }
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }

    public CurrentUser requireOrganizationDoctorPermission(PermissionCode permission, Long organizationId,
            Long doctorId) {
        return requireOrganizationDoctorPermission(permission.code(), organizationId, doctorId);
    }
}
