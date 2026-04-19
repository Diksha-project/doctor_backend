package com.doctor.clinic.DoctorClinic.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.doctor.clinic.DoctorClinic.request.OrganizationRegistrationRequest;
import com.doctor.clinic.DoctorClinic.response.OrganizationDashboardResponse;
import com.doctor.clinic.DoctorClinic.service.OrganizationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/org")
public class OrganizationController {
	
	private OrganizationService  organizationService;
	 public OrganizationController(OrganizationService organizationService) {
	        this.organizationService = organizationService;
	    }
	
	@PostMapping("/register")
	ResponseEntity<String> registerDoctor(@Valid @RequestBody OrganizationRegistrationRequest orgRequest){
        String msg = organizationService.registerOrganization(orgRequest);
		
         return new ResponseEntity<String>(msg, HttpStatus.OK);
		
	}
	
	 @GetMapping("/{organizationId}/dashboard")
	    public ResponseEntity<OrganizationDashboardResponse> getDashboard(
	            @PathVariable Long organizationId) {
	        return ResponseEntity.ok(organizationService.getOrganizationDashboard(organizationId));
	    }
	

}
