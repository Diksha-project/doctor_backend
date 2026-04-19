package com.doctor.clinic.DoctorClinic.model;

public enum AppointmentStatus {
	
	  SCHEDULED,    // Patient booked, waiting for confirmation
      CONFIRMED,    // Doctor confirmed
      COMPLETED,    // Appointment done
      CANCELLED,    // Cancelled
      NO_SHOW,      // Patient didn't show up
      RESCHEDULED   // Rescheduled to another slot

}
