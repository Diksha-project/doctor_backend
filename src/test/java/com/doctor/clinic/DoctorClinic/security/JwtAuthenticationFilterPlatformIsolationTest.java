package com.doctor.clinic.DoctorClinic.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class JwtAuthenticationFilterPlatformIsolationTest {
    private final JwtUtil jwt = mock(JwtUtil.class);
    private final CurrentUserService users = mock(CurrentUserService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwt, users);
    private final CurrentUser platformAdmin = new CurrentUser(12L, 14L, null,
            "platform.admin@archsterhealth.com", "Platform Admin", Set.of("PLATFORM_ADMIN"), Set.of(), Set.of());

    @BeforeEach
    void authenticatePlatformAdmin() {
        when(jwt.isTokenValid("token")).thenReturn(true);
        when(jwt.extractEmail("token")).thenReturn(platformAdmin.email());
        when(users.loadByEmail(platformAdmin.email())).thenReturn(platformAdmin);
    }

    @Test
    void blocksPlatformAdminFromClinicApis() throws Exception {
        MockHttpServletRequest request = authorizedGet("/api/admin/users");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(403, response.getStatus());
        assertEquals("application/json", response.getContentType());
    }

    @Test
    void permitsPlatformAdminApiNamespace() throws Exception {
        MockHttpServletRequest request = authorizedGet("/api/platform/overview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
    }

    private MockHttpServletRequest authorizedGet(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.addHeader("Authorization", "Bearer token");
        return request;
    }
}
