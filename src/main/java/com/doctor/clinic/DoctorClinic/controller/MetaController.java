package com.doctor.clinic.DoctorClinic.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.doctor.clinic.DoctorClinic.request.ConnectRequest;
import com.doctor.clinic.DoctorClinic.service.MetaService;
import com.doctor.clinic.DoctorClinic.service.DoctorIdentityService;
import com.doctor.clinic.DoctorClinic.security.CurrentUserService;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {
	
    private final MetaService metaService;
    private final DoctorIdentityService doctorIdentityService;
    private final CurrentUserService currentUserService;
    
    @PostMapping("/connect")
    public ResponseEntity<?> connect(
            @RequestBody ConnectRequest request) {

        var currentUser = currentUserService.getRequiredCurrentUser();
        metaService.connectDoctor(
                doctorIdentityService.requireDoctor(request.getUserId(), currentUser.organizationId()).getId(),
                request.getCode()
        );

        return ResponseEntity.ok("WhatsApp Connected Successfully");
    }

}
