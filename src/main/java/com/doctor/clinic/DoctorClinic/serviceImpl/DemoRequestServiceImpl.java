package com.doctor.clinic.DoctorClinic.serviceImpl;

import com.doctor.clinic.DoctorClinic.entity.DemoRequest;
import com.doctor.clinic.DoctorClinic.repo.DemoRequestRepo;
import com.doctor.clinic.DoctorClinic.request.CreateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.response.DemoRequestResponse;
import com.doctor.clinic.DoctorClinic.response.DemoRequestSubmissionResponse;
import com.doctor.clinic.DoctorClinic.service.DemoRequestService;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class DemoRequestServiceImpl implements DemoRequestService {
    private static final Set<String> SUPPORTED_STATUSES =
            Set.of("NEW", "CONTACTED", "SCHEDULED", "CONVERTED", "CLOSED");
    private static final String SUBMITTED_MESSAGE =
            "Demo request submitted. Our team will contact you shortly.";

    private final DemoRequestRepo demoRequests;

    @Override
    @Transactional
    public DemoRequestSubmissionResponse create(CreateDemoRequestRequest request) {
        DemoRequest entity = new DemoRequest();
        entity.setOrganizationName(clean(request.getOrganizationName()));
        entity.setContactName(clean(request.getContactName()));
        entity.setEmail(cleanToNull(request.getEmail()));
        entity.setPhone(clean(request.getPhone()));
        entity.setCity(cleanToNull(request.getCity()));
        entity.setExpectedDoctors(request.getExpectedDoctors());
        entity.setMessage(cleanToNull(request.getMessage()));

        DemoRequest saved = demoRequests.save(entity);
        return DemoRequestSubmissionResponse.builder()
                .id(saved.getId())
                .status(saved.getStatus())
                .message(SUBMITTED_MESSAGE)
                .build();
    }

    @Override
    public List<DemoRequestResponse> listAll() {
        return demoRequests.findAllByOrderByRequestedAtDesc().stream().map(this::toResponse).toList();
    }

    @Override
    @Transactional
    public DemoRequestResponse update(Long id, UpdateDemoRequestRequest request) {
        String status = clean(request.getStatus()).toUpperCase(Locale.ROOT);
        if (!SUPPORTED_STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported demo request status");
        }

        DemoRequest entity = demoRequests.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Demo request not found"));
        entity.setStatus(status);
        entity.setInternalNotes(cleanToNull(request.getInternalNotes()));
        return toResponse(demoRequests.save(entity));
    }

    @Override
    public long countByStatus(String status) {
        return demoRequests.countByStatus(status);
    }

    private DemoRequestResponse toResponse(DemoRequest value) {
        return DemoRequestResponse.builder()
                .id(value.getId())
                .organizationName(value.getOrganizationName())
                .contactName(value.getContactName())
                .email(value.getEmail())
                .phone(value.getPhone())
                .city(value.getCity())
                .expectedDoctors(value.getExpectedDoctors())
                .message(value.getMessage())
                .status(value.getStatus())
                .internalNotes(value.getInternalNotes())
                .requestedAt(value.getRequestedAt())
                .updatedAt(value.getUpdatedAt())
                .build();
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }

    private String cleanToNull(String value) {
        String cleaned = clean(value);
        return cleaned == null || cleaned.isEmpty() ? null : cleaned;
    }
}
