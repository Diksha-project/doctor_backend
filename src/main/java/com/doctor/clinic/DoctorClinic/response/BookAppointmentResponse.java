package com.doctor.clinic.DoctorClinic.response;



import lombok.Builder;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@Builder
public class BookAppointmentResponse {
    
	private Long appointmentId;
    private String doctorName;
    private String doctorSpecialization;
    private String patientName;
    private String patientPhone;
    private LocalDate appointmentDate;
    private LocalTime appointmentTime;
    private LocalTime endTime;                    // Added
    private Integer slotDurationMinutes;          // Added
    private BigDecimal consultationFee;
    private BigDecimal finalAmount;
    private String paymentStatus;
    private String appointmentStatus;
    private String paymentLink; // For online payment
    private LocalDateTime createdAt;
    private String message;
}
