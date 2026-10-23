package com.doctor.clinic.DoctorClinic.controller;

import com.doctor.clinic.DoctorClinic.request.CreateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.response.DemoRequestSubmissionResponse;
import com.doctor.clinic.DoctorClinic.service.DemoRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public/demo-requests")
@RequiredArgsConstructor
public class DemoRequestController {
    private final DemoRequestService demoRequestService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DemoRequestSubmissionResponse create(@Valid @RequestBody CreateDemoRequestRequest request) {
        return demoRequestService.create(request);
    }
}
