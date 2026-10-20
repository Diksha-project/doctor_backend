package com.doctor.clinic.DoctorClinic.prescription;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.doctor.clinic.DoctorClinic.repo.*;
import com.doctor.clinic.DoctorClinic.security.*;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PrescriptionServiceTest {
    PrescriptionRepository repo;
    AuthorizationService auth;
    PrescriptionService service;
    UUID id = UUID.randomUUID();
    Prescription prescription;
    @BeforeEach void setup() {
        repo = mock(PrescriptionRepository.class); auth = mock(AuthorizationService.class);
        service = new PrescriptionService(repo, mock(PatientRepo.class), mock(DoctorRepo.class), mock(AppointmentRepo.class), auth, new ObjectMapper());
        prescription = new Prescription(); prescription.setId(id); prescription.setOrganizationId(1L); prescription.setDoctorId(5L);
        when(auth.requireOwnOrganization()).thenReturn(new CurrentUser(1L, 1L, 5L, "doctor", "Doctor", Set.of("DOCTOR"), Set.of(), Set.of(ResourceScope.OWN_DOCTOR)));
        when(repo.findByIdAndOrganizationId(id, 1L)).thenReturn(Optional.of(prescription));
    }
    @Test void anotherTenantIsNotReturned() {
        when(repo.findByIdAndOrganizationId(id, 1L)).thenReturn(Optional.empty());
        assertThrows(ResponseStatusException.class, () -> service.get(id));
    }
    @Test void anotherDoctorCannotFinalizeEvenWithOrganizationScope() {
        when(auth.requireOwnOrganization()).thenReturn(new CurrentUser(1L, 1L, 6L, "admin", "Admin", Set.of("SUPER_ADMIN"), Set.of(), Set.of(ResourceScope.ORGANIZATION)));
        assertThrows(ResponseStatusException.class, () -> service.finalizePrescription(id, 0, true));
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void staleVersionCannotFinalize() {
        prescription.setVersion(2);
        assertThrows(ResponseStatusException.class, () -> service.finalizePrescription(id, 1, true));
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void finalizationRequiresExplicitApproval() {
        assertThrows(ResponseStatusException.class, () -> service.finalizePrescription(id, 0, false));
        verify(repo, never()).saveAndFlush(any());
    }
}
