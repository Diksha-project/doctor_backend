package com.doctor.clinic.DoctorClinic.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PlatformAdminAccessServiceTest {
    private final CurrentUser allowed = new CurrentUser(1L, 5L, null, "ops@archster.test", "Ops",
            Set.of("SUPER_ADMIN"), Set.of(), Set.of(ResourceScope.ORGANIZATION));

    @Test
    void allowsOnlyExplicitlyConfiguredEmail() {
        CurrentUserService users = mock(CurrentUserService.class);
        when(users.getRequiredCurrentUser()).thenReturn(allowed);
        PlatformAdminAccessService access = new PlatformAdminAccessService(users,
                "other@example.com, OPS@archster.test ");

        assertEquals(allowed, access.requirePlatformAdmin());
    }

    @Test
    void organizationSuperAdminIsNotAutomaticallyPlatformAdmin() {
        CurrentUserService users = mock(CurrentUserService.class);
        when(users.getRequiredCurrentUser()).thenReturn(allowed);
        PlatformAdminAccessService access = new PlatformAdminAccessService(users, "other@example.com");

        assertThrows(ResponseStatusException.class, access::requirePlatformAdmin);
    }
}
