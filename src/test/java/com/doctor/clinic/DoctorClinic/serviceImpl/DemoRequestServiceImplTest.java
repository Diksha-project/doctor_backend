package com.doctor.clinic.DoctorClinic.serviceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.doctor.clinic.DoctorClinic.entity.DemoRequest;
import com.doctor.clinic.DoctorClinic.repo.DemoRequestRepo;
import com.doctor.clinic.DoctorClinic.request.CreateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateDemoRequestRequest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class DemoRequestServiceImplTest {
    private final DemoRequestRepo repository = mock(DemoRequestRepo.class);
    private final DemoRequestServiceImpl service = new DemoRequestServiceImpl(repository);

    @Test
    void createsRequestWithoutEmailAndNormalizesOptionalText() {
        when(repository.save(any(DemoRequest.class))).thenAnswer(invocation -> {
            DemoRequest entity = invocation.getArgument(0);
            entity.setId(42L);
            return entity;
        });
        CreateDemoRequestRequest request = new CreateDemoRequestRequest();
        request.setOrganizationName("  Rahul Clinic  ");
        request.setContactName("  Rahul Sharma  ");
        request.setEmail("   ");
        request.setPhone(" +91 98765 43210 ");
        request.setCity("  Delhi  ");
        request.setExpectedDoctors(5);
        request.setMessage("   ");

        var response = service.create(request);

        ArgumentCaptor<DemoRequest> saved = ArgumentCaptor.forClass(DemoRequest.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getOrganizationName()).isEqualTo("Rahul Clinic");
        assertThat(saved.getValue().getEmail()).isNull();
        assertThat(saved.getValue().getCity()).isEqualTo("Delhi");
        assertThat(saved.getValue().getMessage()).isNull();
        assertThat(response.getId()).isEqualTo(42L);
        assertThat(response.getStatus()).isEqualTo("NEW");
    }

    @Test
    void rejectsUnsupportedAdminStatus() {
        UpdateDemoRequestRequest request = new UpdateDemoRequestRequest();
        request.setStatus("unknown");

        assertThrows(ResponseStatusException.class, () -> service.update(1L, request));
    }
}
