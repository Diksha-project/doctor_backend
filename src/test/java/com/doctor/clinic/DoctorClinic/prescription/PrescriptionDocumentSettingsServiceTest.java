package com.doctor.clinic.DoctorClinic.prescription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.security.*;
import com.doctor.clinic.DoctorClinic.serviceImpl.CloudflareR2StorageService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PrescriptionDocumentSettingsServiceTest {
    PrescriptionDocumentTemplateRepository templates;
    OrganizationRepo organizations;
    AuthorizationService authorization;
    PrescriptionDocumentSettingsService service;
    CloudflareR2StorageService storage;
    Organization organization;
    CurrentUser user = new CurrentUser(9L, 1L, 5L, "admin@clinic.com", "Admin",
            Set.of("SUPER_ADMIN"), Set.of("SETTINGS:VIEW", "ORGANIZATION:EDIT", "PRESCRIPTIONS:VIEW"),
            Set.of(ResourceScope.ORGANIZATION));

    @BeforeEach void setup() {
        templates = mock(PrescriptionDocumentTemplateRepository.class);
        organizations = mock(OrganizationRepo.class);
        authorization = mock(AuthorizationService.class);
        storage = mock(CloudflareR2StorageService.class);
        service = new PrescriptionDocumentSettingsService(templates, organizations, mock(DoctorRepo.class),
                authorization, storage, new ObjectMapper());
        when(authorization.requireOwnOrganization()).thenReturn(user);
        when(authorization.requirePermission(anyString())).thenReturn(user);
        organization = new Organization(); organization.setId(1L); organization.setOrganizationName("Clinic One");
        organization.setOwnerMobile("9999999999"); organization.setOwnerEmail("admin@clinic.com");
        when(organizations.findById(1L)).thenReturn(Optional.of(organization));
        when(organizations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test void profileAlwaysUsesAuthenticatedOrganization() {
        assertEquals(1L, service.profile().organizationId());
        verify(organizations).findById(1L);
        verify(organizations, never()).findById(2L);
    }

    @Test void doctorDefaultOverridesOrganizationDefault() throws Exception {
        var orgTemplate = template(null, true, "Organization", "{\"fontFamily\":\"Arial\"}");
        var doctorTemplate = template(5L, true, "Doctor", "{\"fontFamily\":\"Georgia\"}");
        when(templates.findByOrganizationIdAndDoctorIdAndStatusOrderByIsDefaultDescUpdatedAtDesc(1L, 5L, "ACTIVE"))
                .thenReturn(List.of(doctorTemplate));
        when(templates.findByOrganizationIdAndDoctorIdIsNullAndStatusOrderByIsDefaultDescUpdatedAtDesc(1L, "ACTIVE"))
                .thenReturn(List.of(orgTemplate));
        Map<String, Object> snapshot = service.resolveSnapshot(1L, 5L);
        assertEquals("Doctor", snapshot.get("templateName"));
        assertEquals("Georgia", ((Map<?, ?>) snapshot.get("configuration")).get("fontFamily"));
    }

    @Test void defaultTemplateCannotBeArchived() {
        var entity = template(null, true, "Default", "{}");
        when(templates.findByIdAndOrganizationId(entity.getId(), 1L)).thenReturn(Optional.of(entity));
        assertThrows(ResponseStatusException.class, () -> service.setStatus(entity.getId(), "ARCHIVED"));
        verify(templates, never()).saveAndFlush(any());
    }

    @Test void anotherOrganizationsTemplateIsNotReturned() {
        UUID id = UUID.randomUUID();
        when(templates.findByIdAndOrganizationId(id, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.duplicate(id));
    }

    @Test void removingLogoClearsProfileAndDeletesPrivateAsset() {
        organization.setLogoObjectKey("organizations/1/branding/old.png");
        organization.setLogoContentType("image/png"); organization.setLogoFileName("old.png");
        var result = service.removeLogo();
        assertFalse(result.hasLogo());
        verify(storage).delete("organizations/1/branding/old.png");
    }

    private PrescriptionDocumentTemplate template(Long doctorId, boolean isDefault, String name, String config) {
        var value = new PrescriptionDocumentTemplate(); value.setId(UUID.randomUUID()); value.setOrganizationId(1L);
        value.setDoctorId(doctorId); value.setDefault(isDefault); value.setName(name); value.setTemplateType("GENERAL_OPD");
        value.setStatus("ACTIVE"); value.setConfiguration(config); return value;
    }
}
