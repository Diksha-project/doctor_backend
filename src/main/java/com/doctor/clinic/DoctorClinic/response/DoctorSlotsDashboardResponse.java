package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;



@Data
@Builder
public class DoctorSlotsDashboardResponse {
	
	 
    private Long doctorId;
    private String doctorName;
    private String doctorSpecialization;
    private LocalDate currentDate;
    private String currentTime;
    
    // Three main categories
    private List<SlotDetail> upcomingSlots;      // Future slots (not started)
    private List<SlotDetail> ongoingSlots;       // Current/ongoing slots
    private List<SlotDetail> completedSlots;     // Past slots (completed/over)
    private List<SlotDetail> cancelledSlots;     // Cancelled appointments
    
    // Summary counts
    private Summary summary;

}
