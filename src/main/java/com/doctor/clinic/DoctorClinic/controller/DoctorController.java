package com.doctor.clinic.DoctorClinic.controller;


import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.request.BasicDoctorProfileUpdateRequest;
import com.doctor.clinic.DoctorClinic.request.DoctorProfileChangeRequestPayload;
import com.doctor.clinic.DoctorClinic.request.ReviewDoctorProfileChangeRequest;
import com.doctor.clinic.DoctorClinic.model.DoctorProfileChangeStatus;
import com.doctor.clinic.DoctorClinic.serviceImpl.DoctorProfileChangeService;
import com.doctor.clinic.DoctorClinic.request.PatientMsgRequest;
import com.doctor.clinic.DoctorClinic.request.WhatsAppBusinessActivateRequest;
import com.doctor.clinic.DoctorClinic.response.DoctorResponse;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.security.CurrentUser;
import com.doctor.clinic.DoctorClinic.security.CurrentUserService;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;
import com.doctor.clinic.DoctorClinic.service.DoctorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping({"/doctor", "/api/doctor"})
public class DoctorController {
	
	
	@Autowired
	private DoctorService doctorService;

	@Autowired
	private AuthorizationService authorizationService;

	@Autowired
	private CurrentUserService currentUserService;

	@Autowired
	private OrganizationRepo organizationRepo;

	@Autowired
	private DoctorProfileChangeService profileChangeService;
	
	
	@GetMapping("/{userId}/details")
	public ResponseEntity<DoctorResponse> getDoctorDetails(
	        @PathVariable Long userId) {

	    CurrentUser currentUser = currentUserService.getRequiredCurrentUser();
	    authorizationService.requireDoctorProfileAccess(currentUser.organizationId(), userId);
	    DoctorResponse response = doctorService.getDoctorDetails(userId, currentUser.organizationId());

	    return ResponseEntity.ok(response);
	}

	@org.springframework.web.bind.annotation.PutMapping("/{userId}/basic-info")
	public ResponseEntity<DoctorResponse> updateBasicInfo(@PathVariable Long userId,
			@Valid @RequestBody BasicDoctorProfileUpdateRequest request) {
		CurrentUser current = currentUserService.getRequiredCurrentUser();
		authorizationService.requireDoctorPermission(PermissionCode.DOCTORS_EDIT, current.organizationId(), userId);
		return ResponseEntity.ok(doctorService.updateBasicProfile(userId, current.organizationId(), request));
	}

	@PostMapping("/{userId}/profile-change-requests")
	public ResponseEntity<?> submitProfileChange(@PathVariable Long userId,
			@Valid @RequestBody DoctorProfileChangeRequestPayload request) {
		CurrentUser user = currentUserService.getRequiredCurrentUser();
		authorizationService.requireDoctorPermission(PermissionCode.DOCTORS_EDIT, user.organizationId(), userId);
		return ResponseEntity.ok(profileChangeService.submitByUserId(userId, user.organizationId(), user, request));
	}

	@org.springframework.web.bind.annotation.PutMapping("/{userId}/profile")
	public ResponseEntity<?> updateProfileDirectly(@PathVariable Long userId,
			@Valid @RequestBody DoctorProfileChangeRequestPayload request) {
		CurrentUser current = currentUserService.getRequiredCurrentUser();
		CurrentUser user = authorizationService.requireOrganizationPermission(PermissionCode.DOCTORS_EDIT,
				current.organizationId());
		if (!user.isSuperAdmin()) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN,
				"Only Super Admin can update sensitive profile fields directly");
		}
		profileChangeService.updateDirectlyByUserId(userId, current.organizationId(), request);
		return ResponseEntity.ok(Map.of("success", true, "userId", userId));
	}

	@GetMapping("/profile-change-requests")
	public ResponseEntity<?> listProfileChanges(
			@org.springframework.web.bind.annotation.RequestParam(required = false) DoctorProfileChangeStatus status,
			@org.springframework.web.bind.annotation.RequestParam(required = false) Long userId) {
		CurrentUser user = authorizationService.requirePermission(PermissionCode.DOCTORS_VIEW);
		Long scopedUser = user.hasScope(com.doctor.clinic.DoctorClinic.model.ResourceScope.OWN_DOCTOR) ? user.userId() : userId;
		return ResponseEntity.ok(profileChangeService.listByUserId(user.organizationId(), scopedUser, status));
	}

	@PostMapping("/profile-change-requests/{requestId}/approve")
	public ResponseEntity<?> approveProfileChange(@PathVariable Long requestId,
			@RequestBody(required = false) ReviewDoctorProfileChangeRequest request) {
		CurrentUser user = authorizationService.requireOrganizationPermission(PermissionCode.DOCTORS_EDIT,
				currentUserService.getRequiredCurrentUser().organizationId());
		return ResponseEntity.ok(profileChangeService.review(requestId, user.organizationId(), user, true,
				request == null ? null : request.getReviewNote()));
	}

	@PostMapping("/profile-change-requests/{requestId}/reject")
	public ResponseEntity<?> rejectProfileChange(@PathVariable Long requestId,
			@RequestBody(required = false) ReviewDoctorProfileChangeRequest request) {
		CurrentUser user = authorizationService.requireOrganizationPermission(PermissionCode.DOCTORS_EDIT,
				currentUserService.getRequiredCurrentUser().organizationId());
		return ResponseEntity.ok(profileChangeService.review(requestId, user.organizationId(), user, false,
				request == null ? null : request.getReviewNote()));
	}
	
	@PostMapping("/add")
	ResponseEntity<String> registerDoctor( @Valid @RequestBody DoctorRegisterRequest doctorRequest){
		authorizationService.requirePermission(PermissionCode.DOCTORS_EDIT);
		CurrentUser currentUser = currentUserService.getRequiredCurrentUser();
		boolean matchesOwnOrganization = organizationRepo.findByOrganizationName(doctorRequest.getOrganizationName())
				.map(org -> currentUser.organizationId().equals(org.getId()))
				.orElse(false);
		if (!matchesOwnOrganization) {
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.FORBIDDEN, "Invalid organization");
		}
        String msg = doctorService.addNewDoctor(doctorRequest);
		
         return new ResponseEntity<String>(msg, HttpStatus.OK);
		
	}
	
	@PostMapping("/activate")
	public ResponseEntity<Map<String, Object>> activate(@RequestBody WhatsAppBusinessActivateRequest request) {
		System.out.println("Received activation request for user ID: {}"+ request.getUserId());
        
        try {
			CurrentUser current = currentUserService.getRequiredCurrentUser();
			authorizationService.requireDoctorPermission(PermissionCode.WHATSAPP_CONFIGURATION_EDIT, current.organizationId(), request.getUserId());
            Map<String, Object> response = doctorService.activateNumber(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
        	System.out.println("Activation failed: {}"+ e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        }
    }

	
	
	
	

}
