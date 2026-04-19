package com.doctor.clinic.DoctorClinic.request;



	import jakarta.validation.constraints.*;
	import lombok.Data;
	import java.time.LocalDate;
	import java.time.LocalTime;

@Data
public class BookAppointmentRequest {
	    
	    @NotNull(message = "Doctor ID is required")
	    private Long doctorId;
	    
	    @NotBlank(message = "Patient name is required")
	    private String patientName;
	    
	    @NotBlank(message = "Patient phone is required")
	    @Pattern(regexp = "^[6-9]\\d{9}$", message = "Invalid Indian mobile number")
	    private String patientPhone;
	    
	    @Email(message = "Invalid email format")
	    private String patientEmail;
	    
	    private Integer patientAge;
	    private String patientGender; // MALE, FEMALE, OTHER
	    
	    @NotNull(message = "Appointment date is required")
	    private LocalDate appointmentDate;
	    
	    @NotNull(message = "Appointment time is required")
	    private LocalTime appointmentTime;
	    
	    private String reasonForVisit;
	    private String symptoms;
	    
	    @NotBlank(message = "Payment method is required")
	    private String paymentMethod; // CASH, CARD, UPI, ONLINE
	
}
