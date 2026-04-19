package com.doctor.clinic.DoctorClinic.response;

import java.math.BigDecimal;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class AppointmentSummary {
	  private int totalScheduled;
      private int totalConfirmed;
      private int totalCompleted;
      private int totalCancelled;
      private int totalNoShow;
      private int totalPatients;
      private BigDecimal todayEarnings;

}
