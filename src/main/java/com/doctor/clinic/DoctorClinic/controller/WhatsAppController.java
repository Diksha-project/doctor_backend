package com.doctor.clinic.DoctorClinic.controller;


import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;


@RestController
public class WhatsAppController {
    
	
	    
	    // Simple admin endpoint to see registered doctors
	    @GetMapping("/admin/doctors")
	    public String listDoctors() {
	        return "Check console for registered doctors list";
	    }
	    
	    @GetMapping("/health")
	    public String health() {
	        return "WhatsApp Doctor Platform is running!";
	    }
	    
	    @PostMapping("/webhook")
	    public ResponseEntity<String> handleWhatsAppMessage(@RequestBody String payload) {
	        // Parse incoming message
	        // Identify doctor
	        // Generate AI response
	        return ResponseEntity.ok("success");
	    }
	}