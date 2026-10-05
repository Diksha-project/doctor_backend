package com.doctor.clinic.DoctorClinic;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class DoctorClinicApplication {

	public static void main(String[] args) {
		SpringApplication.run(DoctorClinicApplication.class, args);
	}

}
