package com.doctor.clinic.DoctorClinic.controller;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.request.DoctorRegisterRequest;
import com.doctor.clinic.DoctorClinic.request.PatientMsgRequest;
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
	

	
	
	
	

}
