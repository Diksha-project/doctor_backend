package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class OrganizationSummary {
	
	 private int totalDoctors;
     private int activeDoctors;
     private int totalAppointmentsToday;
     private BigDecimal todayRevenue;
     private int availableSlotsToday;

}
