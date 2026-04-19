package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.response.DoctorSlotsDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.SlotDetail;
import com.doctor.clinic.DoctorClinic.response.Summary;
import com.doctor.clinic.DoctorClinic.service.DoctorSlotsService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorSlotsServiceImpl implements DoctorSlotsService {


    
    private final DoctorRepo doctorRepo;
    private final AppointmentRepo appointmentRepo;
    private final DoctorSlotRepo doctorSlotRepo;
    
    public DoctorSlotsDashboardResponse getDoctorSlotsDashboard(Long doctorId) {
        
        Doctor doctor = doctorRepo.findById(doctorId)
            .orElseThrow(() -> new RuntimeException("Doctor not found"));
        
        LocalDate today = LocalDate.now();
        LocalTime currentTime = LocalTime.now();
        
        // Get all appointments for today (non-cancelled)
        List<Appointment> todayAppointments = appointmentRepo
            .findByDoctorIdAndAppointmentDateAndAppointmentStatusNotIn(
                doctorId, today, List.of("CANCELLED", "NO_SHOW"));
        
        // Get all slots for today
        List<DoctorSlot> todaySlots = doctorSlotRepo
            .findByDoctorIdAndSlotDate(doctorId, today);
        
        // Categorize slots
        List<SlotDetail> upcomingSlots = new ArrayList<>();
        List<SlotDetail> ongoingSlots = new ArrayList<>();
        List<SlotDetail> completedSlots = new ArrayList<>();
        List<SlotDetail> cancelledSlots = new ArrayList<>();
        
        BigDecimal todayEarnings = BigDecimal.ZERO;
        
        for (Appointment appointment : todayAppointments) {
            LocalTime appointmentTime = appointment.getAppointmentTime();
            LocalTime endTime = appointment.getEndTime() != null ? 
                appointment.getEndTime() : appointmentTime.plusMinutes(appointment.getSlotDurationMinutes());
            
            SlotDetail slotDetail = mapToSlotDetail(appointment);
            
            // Categorize based on time
            if (endTime.isBefore(currentTime)) {
                // Completed slots (past)
                completedSlots.add(slotDetail);
                if ("PAID".equals(appointment.getPaymentStatus()) || 
                    "COMPLETED".equals(appointment.getAppointmentStatus())) {
                    todayEarnings = todayEarnings.add(appointment.getConsultationFee());
                }
            } 
            else if (appointmentTime.isBefore(currentTime) && endTime.isAfter(currentTime)) {
                // Ongoing slots (currently happening)
                ongoingSlots.add(slotDetail);
            } 
            else if (appointmentTime.isAfter(currentTime)) {
                // Upcoming slots (future)
                upcomingSlots.add(slotDetail);
            }
            
            // Add to cancelled list
            if ("CANCELLED".equals(appointment.getAppointmentStatus()) || 
                "NO_SHOW".equals(appointment.getAppointmentStatus())) {
                cancelledSlots.add(slotDetail);
            }
        }
        
        // Also add empty slots that are available (not booked)
        for (DoctorSlot slot : todaySlots) {
            if (slot.isAvailable() && slot.getBookedCount() < slot.getMaxAppointments()) {
                LocalTime startTime = slot.getStartTime();
                LocalTime endTime = slot.getEndTime();
                
                SlotDetail emptySlot = 
                   SlotDetail.builder()
                        .slotId(slot.getId())
                        .date(slot.getSlotDate())
                        .startTime(startTime)
                        .endTime(endTime)
                        .durationMinutes(slot.getDurationMinutes())
                        .status("AVAILABLE")
                        .patientName("Available Slot")
                        .slotType(slot.getSlotType())
                        .build();               
                if (endTime.isBefore(currentTime)) {
                    // Past empty slots - mark as expired
                    emptySlot.setStatus("EXPIRED");
                    completedSlots.add(emptySlot);
                } else if (startTime.isBefore(currentTime) && endTime.isAfter(currentTime)) {
                    emptySlot.setStatus("ONGOING_AVAILABLE");
                    ongoingSlots.add(emptySlot);
                } else if (startTime.isAfter(currentTime)) {
                    emptySlot.setStatus("AVAILABLE");
                    upcomingSlots.add(emptySlot);
                }
            }
        }
        
        // Build summary
        Summary summary = Summary.builder()
            .totalUpcoming(upcomingSlots.size())
            .totalOngoing(ongoingSlots.size())
            .totalCompleted(completedSlots.size())
            .totalCancelled(cancelledSlots.size())
            .totalPatientsToday(todayAppointments.size())
            .todayEarnings(todayEarnings)
            .build();
        
        // Sort slots by time
        upcomingSlots.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        ongoingSlots.sort((a, b) -> a.getStartTime().compareTo(b.getStartTime()));
        completedSlots.sort((a, b) -> b.getStartTime().compareTo(a.getStartTime())); // Latest first
        
        return DoctorSlotsDashboardResponse.builder()
            .doctorId(doctor.getId())
            .doctorName(doctor.getFullName())
            .doctorSpecialization(doctor.getSpecialization())
            .currentDate(today)
            .currentTime(currentTime.toString())
            .upcomingSlots(upcomingSlots)
            .ongoingSlots(ongoingSlots)
            .completedSlots(completedSlots)
            .cancelledSlots(cancelledSlots)
            .summary(summary)
            .build();
    }
    
    private SlotDetail mapToSlotDetail(Appointment appointment) {
        LocalTime endTime = appointment.getEndTime() != null ? 
            appointment.getEndTime() : 
            appointment.getAppointmentTime().plusMinutes(appointment.getSlotDurationMinutes());
        
        return SlotDetail.builder()
            .appointmentId(appointment.getId())
            .date(appointment.getAppointmentDate())
            .startTime(appointment.getAppointmentTime())
            .endTime(endTime)
            .durationMinutes(appointment.getSlotDurationMinutes())
            .status(appointment.getAppointmentStatus())
            .patientName(appointment.getPatientName())
            .patientPhone(appointment.getPatientPhone())
            .patientAge(appointment.getPatientAge())
            .patientGender(appointment.getPatientGender())
            .reasonForVisit(appointment.getReasonForVisit())
            .paymentStatus(appointment.getPaymentStatus())
            .consultationFee(appointment.getConsultationFee())
            .slotType("BOOKED")
            .build();
    }
}
