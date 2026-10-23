package com.doctor.clinic.DoctorClinic.security;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformAdminAccessService {
    private final CurrentUserService currentUsers;

    public PlatformAdminAccessService(CurrentUserService currentUsers) {
        this.currentUsers = currentUsers;
    }

    public CurrentUser requirePlatformAdmin() {
        CurrentUser user = currentUsers.getRequiredCurrentUser();
        if (!user.isPlatformAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access required");
        }
        return user;
    }
}
