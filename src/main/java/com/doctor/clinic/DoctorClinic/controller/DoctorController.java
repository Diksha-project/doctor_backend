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
@RequestMapping("/doctor")
public class DoctorController {
	
	
	@Autowired
	private DoctorService doctorService;

	@Autowired
	private AuthorizationService authorizationService;

	@Autowired
	private CurrentUserService currentUserService;

	@Autowired
	private OrganizationRepo organizationRepo;
	
	
	@GetMapping("/{doctorId}/details")
	public ResponseEntity<DoctorResponse> getDoctorDetails(
	        @PathVariable Long doctorId) {

	    DoctorResponse response = doctorService.getDoctorDetailsByID(doctorId);
	    authorizationService.requireDoctorPermission(PermissionCode.DOCTORS_VIEW, response.getOrganizationId(), doctorId);

	    return ResponseEntity.ok(response);
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
		System.out.println("Received activation request for doctor ID: {}"+ request.getDoctorId());
        
        try {
            DoctorResponse doctor = doctorService.getDoctorDetailsByID(request.getDoctorId());
            authorizationService.requireDoctorPermission(PermissionCode.WHATSAPP_CONFIGURATION_EDIT, doctor.getOrganizationId(), request.getDoctorId());
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
