package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.doctor.clinic.DoctorClinic.request.OrganizationRegistrationRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateAutomationSettingsRequest;
import com.doctor.clinic.DoctorClinic.response.OrganizationDashboardResponse;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;
import com.doctor.clinic.DoctorClinic.service.OrganizationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/org")
public class OrganizationController {
	
	private OrganizationService  organizationService;
	private final AuthorizationService authorizationService;

	 public OrganizationController(OrganizationService organizationService, AuthorizationService authorizationService) {
	        this.organizationService = organizationService;
	        this.authorizationService = authorizationService;
	    }

	@GetMapping("/{organizationId}/automation-settings")
	public ResponseEntity<?> getAutomationSettings(@PathVariable Long organizationId) {
		authorizationService.requireOrganizationPermission(PermissionCode.ORGANIZATION_VIEW, organizationId);
		return ResponseEntity.ok(organizationService.getAutomationSettings(organizationId));
	}

	@PutMapping("/{organizationId}/automation-settings")
	public ResponseEntity<?> updateAutomationSettings(@PathVariable Long organizationId,
			@Valid @RequestBody UpdateAutomationSettingsRequest request) {
		authorizationService.requireOrganizationPermission(PermissionCode.ORGANIZATION_EDIT, organizationId);
		return ResponseEntity.ok(organizationService.updateAutomationSettings(organizationId, request));
	}
	
	@PostMapping("/register")
	ResponseEntity<String> registerDoctor(@Valid @RequestBody OrganizationRegistrationRequest orgRequest){
        String msg = organizationService.registerOrganization(orgRequest);
		
         return new ResponseEntity<String>(msg, HttpStatus.OK);
		
	}
	
	 @GetMapping("/{organizationId}/dashboard")
	    public ResponseEntity<OrganizationDashboardResponse> getDashboard(
	            @PathVariable Long organizationId) {
	        authorizationService.requireOrganizationPermission(PermissionCode.DASHBOARD_VIEW, organizationId);
	        return ResponseEntity.ok(organizationService.getOrganizationDashboard(organizationId));
	    }
	

}
