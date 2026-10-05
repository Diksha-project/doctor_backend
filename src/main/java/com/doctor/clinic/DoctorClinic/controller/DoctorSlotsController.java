package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.response.DoctorSlotsDashboardResponse;
import com.doctor.clinic.DoctorClinic.service.DoctorSlotsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/doctor")
@RequiredArgsConstructor
public class DoctorSlotsController {
    
    private final DoctorSlotsService doctorSlotsService;
    
    @GetMapping("/{doctorId}/slots-dashboard")
    public ResponseEntity<DoctorSlotsDashboardResponse> getDoctorSlotsDashboard(
            @PathVariable Long doctorId) {
        
        log.info("Fetching slots dashboard for doctor: {}", doctorId);
        
        DoctorSlotsDashboardResponse response = doctorSlotsService.getDoctorSlotsDashboard(
                doctorId, currentOrganizationId());
        return ResponseEntity.ok(response);
    }

    private Long currentOrganizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Number number) {
            return number.longValue();
        }
        throw BusinessException.accessDenied();
    }
}
