package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class Summary {
	
	 private int totalUpcoming;
     private int totalOngoing;
     private int totalCompleted;
     private int totalCancelled;
     private int totalPatientsToday;
     private BigDecimal todayEarnings;

}
