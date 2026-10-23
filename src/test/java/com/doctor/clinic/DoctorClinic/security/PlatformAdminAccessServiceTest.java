package com.doctor.clinic.DoctorClinic.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlatformAdminAccessServiceTest {
    private final CurrentUser allowed = new CurrentUser(1L, 5L, null, "ops@archster.test", "Ops",
            Set.of("PLATFORM_ADMIN"), Set.of(), Set.of());

    @Test
    void allowsPlatformAdminRole() {
        CurrentUserService users = mock(CurrentUserService.class);
        when(users.getRequiredCurrentUser()).thenReturn(allowed);
        PlatformAdminAccessService access = new PlatformAdminAccessService(users);

        assertEquals(allowed, access.requirePlatformAdmin());
    }

    @Test
    void organizationSuperAdminIsNotAutomaticallyPlatformAdmin() {
        CurrentUserService users = mock(CurrentUserService.class);
        CurrentUser organizationAdmin = new CurrentUser(2L, 5L, null, "admin@clinic.test", "Admin",
                Set.of("SUPER_ADMIN"), Set.of(), Set.of());
        when(users.getRequiredCurrentUser()).thenReturn(organizationAdmin);
        PlatformAdminAccessService access = new PlatformAdminAccessService(users);

        assertThrows(ResponseStatusException.class, access::requirePlatformAdmin);
    }
}
