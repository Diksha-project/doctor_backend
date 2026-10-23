package com.doctor.clinic.DoctorClinic.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.model.ApiResponse;
import com.doctor.clinic.DoctorClinic.request.ApproveAppointmentOverflowRequest;
import com.doctor.clinic.DoctorClinic.request.CreateAppointmentOverflowRequest;
import com.doctor.clinic.DoctorClinic.response.AppointmentOverflowResponse;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.security.CurrentUser;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;
import com.doctor.clinic.DoctorClinic.serviceImpl.AppointmentOverflowService;
import com.doctor.clinic.DoctorClinic.service.DoctorIdentityService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/appointment-overflow")
@RequiredArgsConstructor
public class AppointmentOverflowController {
    private final AppointmentOverflowService service;
    private final AuthorizationService authorizationService;
    private final DoctorIdentityService doctorIdentityService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppointmentOverflowResponse>>> list() {
        CurrentUser user = authorizationService.requireOrganizationPermission(PermissionCode.APPOINTMENTS_VIEW, orgId());
        Long doctorId = user.hasScope(com.doctor.clinic.DoctorClinic.model.ResourceScope.OWN_DOCTOR)
                ? doctorIdentityService.requireDoctor(user.userId(), user.organizationId()).getId() : null;
        return ResponseEntity.ok(ApiResponse.success(service.list(orgId(), doctorId)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<AppointmentOverflowResponse>> create(@Valid @RequestBody CreateAppointmentOverflowRequest input) {
        authorizationService.requireDoctorPermission(PermissionCode.APPOINTMENTS_EDIT, orgId(), input.getUserId());
        return ResponseEntity.ok(ApiResponse.success("Approval request created", service.create(input, orgId())));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<ApiResponse<AppointmentOverflowResponse>> approve(@PathVariable Long id,
            @Valid @RequestBody ApproveAppointmentOverflowRequest input) {
        CurrentUser user = authorizationService.requireOrganizationPermission(PermissionCode.APPOINTMENTS_EDIT, orgId());
        return ResponseEntity.ok(ApiResponse.success("Approval request updated", service.approve(id, orgId(), input, user)));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<ApiResponse<AppointmentOverflowResponse>> reject(@PathVariable Long id) {
        CurrentUser user = authorizationService.requireOrganizationPermission(PermissionCode.APPOINTMENTS_EDIT, orgId());
        return ResponseEntity.ok(ApiResponse.success("Approval request rejected", service.reject(id, orgId(), user)));
    }

    private Long orgId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object details = auth == null ? null : auth.getDetails();
        if (details instanceof Number number) return number.longValue();
        throw BusinessException.accessDenied();
    }
}
