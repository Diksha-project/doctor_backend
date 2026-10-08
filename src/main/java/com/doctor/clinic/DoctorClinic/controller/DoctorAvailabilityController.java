package com.doctor.clinic.DoctorClinic.controller;

import java.time.LocalDate;

import jakarta.validation.Valid;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.model.ApiResponse;
import com.doctor.clinic.DoctorClinic.request.AvailabilityExceptionRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateAvailabilitySettingsRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateWeeklyAvailabilityRequest;
import com.doctor.clinic.DoctorClinic.response.DoctorAvailabilityResponse;
import com.doctor.clinic.DoctorClinic.response.SlotGenerationResultResponse;
import com.doctor.clinic.DoctorClinic.service.DoctorAvailabilityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Lets a doctor (or the organization admin on their behalf) configure a
 * recurring weekly schedule, day-specific exceptions, and booking settings.
 * Saving any of these immediately (re)generates bookable slots so patients
 * stop seeing "no slots available" once a schedule is configured.
 */
@Slf4j
@RestController
@RequestMapping("/api/doctor/{doctorId}/availability")
@RequiredArgsConstructor
public class DoctorAvailabilityController {

    private final DoctorAvailabilityService doctorAvailabilityService;

    @GetMapping
    public ResponseEntity<ApiResponse<DoctorAvailabilityResponse>> getAvailability(@PathVariable Long doctorId) {
        DoctorAvailabilityResponse response = doctorAvailabilityService.getAvailability(doctorId, organizationId());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/schedule")
    public ResponseEntity<ApiResponse<DoctorAvailabilityResponse>> updateWeeklySchedule(@PathVariable Long doctorId,
            @Valid @RequestBody UpdateWeeklyAvailabilityRequest request) {
        DoctorAvailabilityResponse response = doctorAvailabilityService.updateWeeklySchedule(doctorId,
                organizationId(), request);
        return ResponseEntity.ok(ApiResponse.success("Weekly schedule updated", response));
    }

    @PutMapping("/settings")
    public ResponseEntity<ApiResponse<DoctorAvailabilityResponse>> updateSettings(@PathVariable Long doctorId,
            @Valid @RequestBody UpdateAvailabilitySettingsRequest request) {
        DoctorAvailabilityResponse response = doctorAvailabilityService.updateSettings(doctorId, organizationId(),
                request);
        return ResponseEntity.ok(ApiResponse.success("Availability settings updated", response));
    }

    @PostMapping("/exceptions")
    public ResponseEntity<ApiResponse<DoctorAvailabilityResponse>> addOrUpdateException(@PathVariable Long doctorId,
            @Valid @RequestBody AvailabilityExceptionRequest request) {
        DoctorAvailabilityResponse response = doctorAvailabilityService.addOrUpdateException(doctorId,
                organizationId(), request);
        return ResponseEntity.ok(ApiResponse.success("Exception saved", response));
    }

    @DeleteMapping("/exceptions/{date}")
    public ResponseEntity<ApiResponse<DoctorAvailabilityResponse>> removeException(@PathVariable Long doctorId,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        DoctorAvailabilityResponse response = doctorAvailabilityService.removeException(doctorId, organizationId(),
                date);
        return ResponseEntity.ok(ApiResponse.success("Exception removed", response));
    }

    @PostMapping("/regenerate-slots")
    public ResponseEntity<ApiResponse<SlotGenerationResultResponse>> regenerateSlots(@PathVariable Long doctorId) {
        // Re-validates org ownership before touching slots for this doctor.
        doctorAvailabilityService.getAvailability(doctorId, organizationId());
        SlotGenerationResultResponse response = doctorAvailabilityService.generateSlots(doctorId);
        return ResponseEntity.ok(ApiResponse.success("Slots regenerated", response));
    }

    private Long organizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Number number) {
            return number.longValue();
        }
        throw BusinessException.accessDenied();
    }
}
