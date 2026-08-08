package com.doctor.clinic.DoctorClinic.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.doctor.clinic.DoctorClinic.request.ConnectRequest;
import com.doctor.clinic.DoctorClinic.service.MetaService;

@RestController
@RequestMapping("/api/meta")
@RequiredArgsConstructor
public class MetaController {
	
    private final MetaService metaService;

    @PostMapping("/connect")
    public ResponseEntity<?> connect(
            @RequestBody ConnectRequest request) {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getPrincipal().toString();

        metaService.connectDoctor(email, request.getCode());

        return ResponseEntity.ok("WhatsApp Connected Successfully");
    }

}
