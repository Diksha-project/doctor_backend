package com.doctor.clinic.DoctorClinic.security;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlatformAdminAccessService {
    private final CurrentUserService currentUsers;
    private final Set<String> allowedEmails;

    public PlatformAdminAccessService(CurrentUserService currentUsers,
            @Value("${app.platform-admin-emails:}") String configuredEmails) {
        this.currentUsers = currentUsers;
        this.allowedEmails = Arrays.stream(configuredEmails.split(","))
                .map(String::trim).filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet());
    }

    public CurrentUser requirePlatformAdmin() {
        CurrentUser user = currentUsers.getRequiredCurrentUser();
        if (!allowedEmails.contains(user.email().toLowerCase(Locale.ROOT))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform administrator access required");
        }
        return user;
    }
}
