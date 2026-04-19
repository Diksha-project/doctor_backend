package com.doctor.clinic.DoctorClinic.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AddressDto {
	private String addressLine1;
	private String addressLine2;
	private String city;
	private String state;
	private String pincode;
	private String country;
}