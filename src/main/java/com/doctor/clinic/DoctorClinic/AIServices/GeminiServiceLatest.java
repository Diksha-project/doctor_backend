package com.doctor.clinic.DoctorClinic.AIServices;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import org.springframework.web.reactive.function.client.WebClient;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeminiServiceLatest {

    private String model = "gemini-2.5-flash";

    @Value("${gemini.api.key}")
    private String apiKey;

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private AppointmentRepo appointmentRepo;
    
    @Autowired
    private DoctorSlotRepo doctorSlotRepo;
    
    public GeminiServiceLatest() {
        this.webClient = WebClient.builder().build();
        this.objectMapper = new ObjectMapper();
    }
    
    public String generateResponse(String patientMessage, Doctor doctor) {
        if (apiKey == null || apiKey.isEmpty()) {
            return getFallbackResponse(doctor);
        }
        
        try {
            // Load all necessary data
            String doctorContext = buildDoctorContext(doctor);
            String appointmentsContext = buildAppointmentsContext(doctor);
            String slotsContext = buildSlotsContext(doctor);
            
            String prompt = buildPromptWithFullContext(patientMessage, doctor, 
                doctorContext, appointmentsContext, slotsContext);
            
            Map<String, Object> requestBody = new HashMap<>();
            Map<String, Object> content = new HashMap<>();
            content.put("parts", List.of(Map.of("text", prompt)));
            requestBody.put("contents", List.of(content));
            
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
            
            String response = webClient.post()
                .uri(url)
                .header("Content-Type", "application/json")
                .bodyValue(requestBody)
                .retrieve()
                .bodyToMono(String.class)
                .block();
            
            JsonNode jsonNode = objectMapper.readTree(response);
            return jsonNode
                .path("candidates")
                .get(0)
                .path("content")
                .path("parts")
                .get(0)
                .path("text")
                .asText();
            
        } catch (Exception e) {
            log.error("AI Error: {}", e.getMessage());
            return getFallbackResponse(doctor);
        }
    }
    
    private String buildDoctorContext(Doctor doctor) {
        return String.format("""
            === DOCTOR DETAILS ===
            - Name: Dr. %s
            - Specialization: %s
            - Qualification: %s
            - Experience: %d years
            - Consultation Fee: ₹%s
            - Regular Hours: %s
            - Clinic/Hospital: %s
            - Status: %s
            """,
            doctor.getFullName(),
            doctor.getSpecialization() != null ? doctor.getSpecialization() : "General Physician",
            doctor.getQualification() != null ? doctor.getQualification() : "MBBS",
            doctor.getExperienceYears() != null ? doctor.getExperienceYears() : 0,
            doctor.getConsultationFee(),
            doctor.getConsultationHours(),
            doctor.getOrganization() != null ? doctor.getOrganization().getOrganizationName() : "Our Clinic",
            doctor.getStatus()
        );
    }
    
    private String buildAppointmentsContext(Doctor doctor) {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        
        // Get today's appointments
        List<Appointment> todayAppointments = appointmentRepo
            .findByDoctorIdAndAppointmentDate(doctor.getId(), today);
        
        // Get upcoming appointments (next 7 days)
        List<Appointment> upcomingAppointments = appointmentRepo
            .findByDoctorIdAndAppointmentDateBetween(doctor.getId(), today, nextWeek);
        
        // Get past appointments (last 7 days)
        List<Appointment> pastAppointments = appointmentRepo
            .findByDoctorIdAndAppointmentDateBetween(doctor.getId(), today.minusDays(7), today.minusDays(1));
        
        StringBuilder context = new StringBuilder();
        
        // Today's schedule
        context.append("=== TODAY'S APPOINTMENTS (").append(today).append(") ===\n");
        if (todayAppointments.isEmpty()) {
            context.append("No appointments scheduled for today.\n\n");
        } else {
            for (Appointment app : todayAppointments) {
                context.append(String.format("- %s: %s (%s) - %s\n",
                    app.getAppointmentTime(),
                    app.getPatientName(),
                    app.getAppointmentStatus(),
                    app.isPaid() ? "Paid" : "Payment Pending"
                ));
            }
            context.append("\n");
        }
        
        // Upcoming appointments
        context.append("=== UPCOMING APPOINTMENTS (Next 7 days) ===\n");
        if (upcomingAppointments.isEmpty()) {
            context.append("No upcoming appointments scheduled.\n\n");
        } else {
            for (Appointment app : upcomingAppointments) {
                context.append(String.format("- %s at %s: %s (%s)\n",
                    app.getAppointmentDate(),
                    app.getAppointmentTime(),
                    app.getPatientName(),
                    app.getAppointmentStatus()
                ));
            }
            context.append("\n");
        }
        
        // Past appointments summary
        context.append("=== PAST WEEK APPOINTMENTS ===\n");
        context.append(String.format("Total completed appointments in last 7 days: %d\n", pastAppointments.size()));
        
        // Count completed vs cancelled
        long completedCount = pastAppointments.stream()
            .filter(a -> "COMPLETED".equals(a.getAppointmentStatus()))
            .count();
        long cancelledCount = pastAppointments.stream()
            .filter(a -> "CANCELLED".equals(a.getAppointmentStatus()))
            .count();
        
        context.append(String.format("- Completed: %d\n", completedCount));
        context.append(String.format("- Cancelled/No-Show: %d\n\n", cancelledCount));
        
        return context.toString();
    }
    
    private String buildSlotsContext(Doctor doctor) {
        LocalDate today = LocalDate.now();
        LocalDate nextWeek = today.plusDays(7);
        
        List<DoctorSlot> availableSlots = doctorSlotRepo
            .findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(doctor.getId(), today, nextWeek);
        
        // Group slots by date
        Map<LocalDate, List<DoctorSlot>> slotsByDate = availableSlots.stream()
            .collect(Collectors.groupingBy(DoctorSlot::getSlotDate));
        
        StringBuilder context = new StringBuilder();
        context.append("=== AVAILABLE SLOTS (Next 7 days) ===\n");
        
        if (availableSlots.isEmpty()) {
            context.append("No available slots found for the next 7 days.\n\n");
        } else {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("EEEE, MMM d");
            for (Map.Entry<LocalDate, List<DoctorSlot>> entry : slotsByDate.entrySet()) {
                context.append(String.format("\n📅 %s:\n", entry.getKey().format(dateFormatter)));
                for (DoctorSlot slot : entry.getValue()) {
                    context.append(String.format("   ⏰ %s - %s (%d min) - %d/%d booked\n",
                        slot.getStartTime(),
                        slot.getEndTime(),
                        slot.getDurationMinutes(),
                        slot.getBookedCount(),
                        slot.getMaxAppointments()
                    ));
                }
            }
            context.append("\n");
        }
        
        return context.toString();
    }
    
    private String buildPromptWithFullContext(String message, Doctor doctor, 
                                               String doctorContext, 
                                               String appointmentsContext, 
                                               String slotsContext) {
        return String.format("""
            You are Dr. %s's intelligent medical assistant. Answer the patient's query using the real-time data below.
            
            %s
            
            %s
            
            %s
            
            === PATIENT QUERY ===
            "%s"
            
            === INSTRUCTIONS ===
            1. Use the REAL appointment and slot data above to answer accurately
            2. If patient asks about availability, check AVAILABLE SLOTS and suggest specific times
            3. If patient wants to book, ask for their name and preferred time from available slots
            4. If patient asks about today's schedule, tell them using TODAY'S APPOINTMENTS
            5. Always mention consultation fee if asked
            6. Be polite, helpful, and concise (2-4 sentences)
            7. If a requested time is not available, suggest alternative slots
            8. Don't give medical advice - recommend booking appointment for symptoms
            
            Your response as Dr. %s's assistant:
            """,
            doctor.getFullName(),
            doctorContext,
            appointmentsContext,
            slotsContext,
            message,
            doctor.getFullName()
        );
    }
    
    private String getFallbackResponse(Doctor doctor) {
        return String.format("Dr. %s is available %s. Fee: ₹%s. Would you like to book an appointment?",
            doctor.getFullName(),
            doctor.getConsultationHours(),
            doctor.getConsultationFee()
        );
    }
}
