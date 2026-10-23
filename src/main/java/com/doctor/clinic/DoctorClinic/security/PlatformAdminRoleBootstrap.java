package com.doctor.clinic.DoctorClinic.security;

import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;
import com.doctor.clinic.DoctorClinic.repo.RoleRepo;
import com.doctor.clinic.DoctorClinic.repo.UserRoleRepo;
import jakarta.transaction.Transactional;
import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformAdminRoleBootstrap implements ApplicationRunner {
    private final AppUserRepo users;
    private final RoleRepo roles;
    private final UserRoleRepo userRoles;

    @Value("${app.platform-admin-emails:}")
    private String configuredEmails;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (configuredEmails == null || configuredEmails.isBlank()) {
            return;
        }

        var platformRole = roles.findWithPermissionsByCodeAndOrganizationIsNull("PLATFORM_ADMIN")
                .orElseThrow(() -> new IllegalStateException("PLATFORM_ADMIN role is missing"));

        Arrays.stream(configuredEmails.split(","))
                .map(String::trim)
                .filter(email -> !email.isEmpty())
                .distinct()
                .forEach(email -> users.findDetailedByEmailIgnoreCase(email).ifPresentOrElse(user -> {
                    if (users.findActiveRoleCodesByUserId(user.getId()).equals(java.util.Set.of("PLATFORM_ADMIN"))) {
                        return;
                    }
                    userRoles.deleteAllByUserId(user.getId());
                    UserRole assignment = new UserRole();
                    assignment.setUser(user);
                    assignment.setRole(platformRole);
                    assignment.setScope(ResourceScope.ORGANIZATION);
                    userRoles.save(assignment);
                    log.info("Assigned isolated PLATFORM_ADMIN role to {}", user.getEmail());
                }, () -> log.warn("Configured platform administrator account does not exist: {}", email)));
    }
}
