package com.doctor.clinic.DoctorClinic.controller;


import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.request.PatientMsgRequest;
import com.doctor.clinic.DoctorClinic.request.WhatsAppBusinessActivateRequest;
import com.doctor.clinic.DoctorClinic.service.DoctorService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/doctor")
public class DoctorController {
	
	
	@Autowired
	private DoctorService doctorService;
	
	@PostMapping("/add")
	ResponseEntity<String> registerDoctor( @Valid @RequestBody DoctorRegisterRequest doctorRequest){
        String msg = doctorService.addNewDoctor(doctorRequest);
		
         return new ResponseEntity<String>(msg, HttpStatus.OK);
		
	}
	
	@PostMapping("/activate")
	public ResponseEntity<Map<String, Object>> activate(@RequestBody WhatsAppBusinessActivateRequest request) {
		System.out.println("Received activation request for doctor ID: {}"+ request.getDoctorId());
        
        try {
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
