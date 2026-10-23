package com.doctor.clinic.DoctorClinic.controller;

import com.doctor.clinic.DoctorClinic.entity.DemoRequest;
import com.doctor.clinic.DoctorClinic.repo.DemoRequestRepo;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public/demo-requests")
@RequiredArgsConstructor
public class DemoRequestController {
    private final DemoRequestRepo requests;

    public record DemoRequestInput(
            @NotBlank @Size(max = 150) String organizationName,
            @NotBlank @Size(max = 120) String contactName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Pattern(regexp = "^[+0-9 ()-]{8,30}$") String phone,
            @NotBlank @Pattern(regexp = "HOSPITAL|CLINIC|OTHER") String organizationType,
            @Size(max = 120) String city,
            @Min(1) @Max(10000) Integer expectedDoctors,
            @Size(max = 1000) String message) {}

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> create(@Valid @RequestBody DemoRequestInput input) {
        DemoRequest request = new DemoRequest();
        request.setOrganizationName(clean(input.organizationName()));
        request.setContactName(clean(input.contactName()));
        request.setEmail(clean(input.email()).toLowerCase(Locale.ROOT));
        request.setPhone(clean(input.phone()));
        request.setOrganizationType(input.organizationType());
        request.setCity(clean(input.city()));
        request.setExpectedDoctors(input.expectedDoctors());
        request.setMessage(clean(input.message()));
        DemoRequest saved = requests.save(request);
        return Map.of("id", saved.getId(), "status", saved.getStatus(),
                "message", "Demo request submitted. Our team will contact you shortly.");
    }

    private String clean(String value) {
        return value == null ? null : value.trim();
    }
}
