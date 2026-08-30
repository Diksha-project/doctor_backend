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
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class GeminiServiceLatest {

    private static final String MODEL = "gemini-2.5-flash";

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
            + MODEL
            + ":generateContent";

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

    /**
     * ============================================================
     * GENERATE AI RESPONSE
     * ============================================================
     */
    public String generateResponse(String patientMessage, Doctor doctor) {

        log.info("=================================");
        log.info("GEMINI REQUEST STARTED");
        log.info("=================================");

        log.info("Doctor ID = {}", doctor.getId());
        log.info("Doctor Name = {}", doctor.getFullName());
        log.info("Patient Message = {}", patientMessage);
        log.info("Gemini Model = {}", MODEL);

        if (apiKey == null || apiKey.isBlank()) {

            log.error("Gemini API key is missing");

            return getFallbackResponse(doctor);
        }

        try {

            /*
             * =====================================================
             * STEP 1: BUILD DOCTOR CONTEXT
             * =====================================================
             */

            String doctorContext =
                    buildDoctorContext(doctor);

            /*
             * =====================================================
             * STEP 2: BUILD APPOINTMENT CONTEXT
             * =====================================================
             */

            String appointmentsContext =
                    buildAppointmentsContext(doctor);

            /*
             * =====================================================
             * STEP 3: BUILD AVAILABLE SLOT CONTEXT
             * =====================================================
             */

            String slotsContext =
                    buildSlotsContext(doctor);

            /*
             * =====================================================
             * STEP 4: BUILD PROMPT
             * =====================================================
             */

            String prompt =
                    buildPromptWithFullContext(
                            patientMessage,
                            doctor,
                            doctorContext,
                            appointmentsContext,
                            slotsContext
                    );

            log.info("Gemini prompt created successfully");
            log.debug("Gemini prompt = {}", prompt);

            /*
             * =====================================================
             * STEP 5: BUILD REQUEST BODY
             *
             * Gemini expects:
             *
             * {
             *   "contents": [
             *      {
             *          "role": "user",
             *          "parts": [
             *              {
             *                  "text": "..."
             *              }
             *          ]
             *      }
             *   ]
             * }
             * =====================================================
             */

            Map<String, Object> part =
                    new HashMap<>();

            part.put(
                    "text",
                    prompt
            );

            Map<String, Object> content =
                    new HashMap<>();

            content.put(
                    "role",
                    "user"
            );

            content.put(
                    "parts",
                    List.of(part)
            );

            Map<String, Object> requestBody =
                    new HashMap<>();

            requestBody.put(
                    "contents",
                    List.of(content)
            );

            /*
             * =====================================================
             * STEP 6: CALL GEMINI
             * =====================================================
             */

            log.info("Calling Gemini API...");
            log.info("Gemini URL = {}", GEMINI_URL);

            String response =
                    webClient.post()

                            .uri(GEMINI_URL)

                            .header(
                                    "Content-Type",
                                    "application/json"
                            )

                            /*
                             * IMPORTANT:
                             *
                             * Send API key through header
                             * instead of URL.
                             */
                            .header(
                                    "x-goog-api-key",
                                    apiKey
                            )

                            .bodyValue(requestBody)

                            .retrieve()

                            .bodyToMono(String.class)

                            .block();

            /*
             * =====================================================
             * STEP 7: LOG RAW RESPONSE
             * =====================================================
             */

            log.info("Gemini API call successful");

            log.debug(
                    "Gemini raw response = {}",
                    response
            );

            /*
             * =====================================================
             * STEP 8: PARSE RESPONSE
             * =====================================================
             */

            JsonNode jsonNode =
                    objectMapper.readTree(response);

            JsonNode candidates =
                    jsonNode.path("candidates");

            if (!candidates.isArray()
                    || candidates.isEmpty()) {

                log.error(
                        "Gemini response does not contain candidates"
                );

                log.error(
                        "Gemini response = {}",
                        response
                );

                return getFallbackResponse(doctor);
            }

            JsonNode firstCandidate =
                    candidates.get(0);

            JsonNode contentNode =
                    firstCandidate.path("content");

            JsonNode parts =
                    contentNode.path("parts");

            if (!parts.isArray()
                    || parts.isEmpty()) {

                log.error(
                        "Gemini response does not contain parts"
                );

                log.error(
                        "Gemini response = {}",
                        response
                );

                return getFallbackResponse(doctor);
            }

            String aiResponse =
                    parts.get(0)
                            .path("text")
                            .asText();

            if (aiResponse == null
                    || aiResponse.isBlank()) {

                log.error(
                        "Gemini returned empty response"
                );

                return getFallbackResponse(doctor);
            }

            /*
             * =====================================================
             * STEP 9: SUCCESS
             * =====================================================
             */

            log.info("=================================");
            log.info("GEMINI RESPONSE SUCCESS");
            log.info("=================================");

            log.info(
                    "AI Response = {}",
                    aiResponse
            );

            return aiResponse;

        } catch (WebClientResponseException e) {

            /*
             * =====================================================
             * GEMINI HTTP ERROR
             * =====================================================
             */

            log.error("=================================");
            log.error("GEMINI API HTTP ERROR");
            log.error("=================================");

            log.error(
                    "HTTP Status = {}",
                    e.getStatusCode()
            );

            log.error(
                    "Response Body = {}",
                    e.getResponseBodyAsString()
            );

            return getFallbackResponse(doctor);

        } catch (Exception e) {

            /*
             * =====================================================
             * GENERAL ERROR
             * =====================================================
             */

            log.error("=================================");
            log.error("GEMINI GENERAL ERROR");
            log.error("=================================");

            log.error(
                    "Error Type = {}",
                    e.getClass().getName()
            );

            log.error(
                    "Error Message = {}",
                    e.getMessage(),
                    e
            );

            return getFallbackResponse(doctor);
        }
    }

    /**
     * ============================================================
     * DOCTOR CONTEXT
     * ============================================================
     */
    private String buildDoctorContext(Doctor doctor) {

        return String.format(
                """
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

                doctor.getSpecialization() != null
                        ? doctor.getSpecialization()
                        : "General Physician",

                doctor.getQualification() != null
                        ? doctor.getQualification()
                        : "MBBS",

                doctor.getExperienceYears() != null
                        ? doctor.getExperienceYears()
                        : 0,

                doctor.getConsultationFee(),

                doctor.getConsultationHours(),

                doctor.getOrganization() != null
                        ? doctor.getOrganization()
                                .getOrganizationName()
                        : "Our Clinic",

                doctor.getStatus()
        );
    }

    /**
     * ============================================================
     * APPOINTMENTS CONTEXT
     * ============================================================
     */
    private String buildAppointmentsContext(Doctor doctor) {

        LocalDate today =
                LocalDate.now();

        LocalDate nextWeek =
                today.plusDays(7);

        /*
         * Today's appointments
         */
        List<Appointment> todayAppointments =
                appointmentRepo
                        .findByDoctorIdAndAppointmentDate(
                                doctor.getId(),
                                today
                        );

        /*
         * Upcoming appointments
         */
        List<Appointment> upcomingAppointments =
                appointmentRepo
                        .findByDoctorIdAndAppointmentDateBetween(
                                doctor.getId(),
                                today,
                                nextWeek
                        );

        /*
         * Past appointments
         */
        List<Appointment> pastAppointments =
                appointmentRepo
                        .findByDoctorIdAndAppointmentDateBetween(
                                doctor.getId(),
                                today.minusDays(7),
                                today.minusDays(1)
                        );

        StringBuilder context =
                new StringBuilder();

        /*
         * =====================================================
         * TODAY
         * =====================================================
         */

        context.append(
                "=== TODAY'S APPOINTMENTS ("
        )
        .append(today)
        .append(") ===\n");

        if (todayAppointments.isEmpty()) {

            context.append(
                    "No appointments scheduled for today.\n\n"
            );

        } else {

            for (Appointment app :
                    todayAppointments) {

                context.append(
                        String.format(
                                "- %s: %s (%s) - %s\n",

                                app.getAppointmentTime(),

                                app.getPatientName(),

                                app.getAppointmentStatus(),

                                app.isPaid()
                                        ? "Paid"
                                        : "Payment Pending"
                        )
                );
            }

            context.append("\n");
        }

        /*
         * =====================================================
         * UPCOMING
         * =====================================================
         */

        context.append(
                "=== UPCOMING APPOINTMENTS (Next 7 days) ===\n"
        );

        if (upcomingAppointments.isEmpty()) {

            context.append(
                    "No upcoming appointments scheduled.\n\n"
            );

        } else {

            for (Appointment app :
                    upcomingAppointments) {

                context.append(
                        String.format(
                                "- %s at %s: %s (%s)\n",

                                app.getAppointmentDate(),

                                app.getAppointmentTime(),

                                app.getPatientName(),

                                app.getAppointmentStatus()
                        )
                );
            }

            context.append("\n");
        }

        /*
         * =====================================================
         * PAST WEEK
         * =====================================================
         */

        context.append(
                "=== PAST WEEK APPOINTMENTS ===\n"
        );

        context.append(
                String.format(
                        "Total appointments in last 7 days: %d\n",
                        pastAppointments.size()
                )
        );

        long completedCount =
                pastAppointments.stream()
                        .filter(a ->
                                "COMPLETED"
                                        .equals(a.getAppointmentStatus()))
                        .count();

        long cancelledCount =
                pastAppointments.stream()
                        .filter(a ->
                                "CANCELLED"
                                        .equals(a.getAppointmentStatus()))
                        .count();

        context.append(
                String.format(
                        "- Completed: %d\n",
                        completedCount
                )
        );

        context.append(
                String.format(
                        "- Cancelled/No-Show: %d\n\n",
                        cancelledCount
                )
        );

        return context.toString();
    }

    /**
     * ============================================================
     * AVAILABLE SLOTS CONTEXT
     * ============================================================
     */
    private String buildSlotsContext(Doctor doctor) {

        LocalDate today =
                LocalDate.now();

        LocalDate nextWeek =
                today.plusDays(7);

        List<DoctorSlot> availableSlots =
                doctorSlotRepo
                        .findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(
                                doctor.getId(),
                                today,
                                nextWeek
                        );

        Map<LocalDate, List<DoctorSlot>> slotsByDate =
                availableSlots.stream()
                        .collect(
                                Collectors.groupingBy(
                                        DoctorSlot::getSlotDate
                                )
                        );

        StringBuilder context =
                new StringBuilder();

        context.append(
                "=== AVAILABLE SLOTS (Next 7 days) ===\n"
        );

        if (availableSlots.isEmpty()) {

            context.append(
                    "No available slots found for the next 7 days.\n\n"
            );

        } else {

            DateTimeFormatter dateFormatter =
                    DateTimeFormatter.ofPattern(
                            "EEEE, MMM d"
                    );

            for (
                    Map.Entry<
                            LocalDate,
                            List<DoctorSlot>
                    > entry
                    : slotsByDate.entrySet()
            ) {

                context.append(
                        String.format(
                                "\n📅 %s:\n",
                                entry.getKey()
                                        .format(dateFormatter)
                        )
                );

                for (
                        DoctorSlot slot
                        : entry.getValue()
                ) {

                    context.append(
                            String.format(
                                    "   ⏰ %s - %s (%d min) - %d/%d booked\n",

                                    slot.getStartTime(),

                                    slot.getEndTime(),

                                    slot.getDurationMinutes(),

                                    slot.getBookedCount(),

                                    slot.getMaxAppointments()
                            )
                    );
                }
            }

            context.append("\n");
        }

        return context.toString();
    }

    /**
     * ============================================================
     * PROMPT
     * ============================================================
     */
    private String buildPromptWithFullContext(
            String message,
            Doctor doctor,
            String doctorContext,
            String appointmentsContext,
            String slotsContext) {

        return String.format(
                """
                You are Dr. %s's intelligent medical assistant.
                Answer the patient's query using the real-time data below.

                %s

                %s

                %s

                === PATIENT QUERY ===
                "%s"

                === INSTRUCTIONS ===
                1. Use the REAL appointment and slot data above to answer accurately.
                2. If the patient asks about availability, check AVAILABLE SLOTS and suggest specific times.
                3. If the patient wants to book, ask for their name and preferred time from available slots.
                4. If the patient asks about today's schedule, use TODAY'S APPOINTMENTS.
                5. Always mention consultation fee if asked.
                6. Be polite, helpful, and concise. Keep responses within 2-4 sentences.
                7. If a requested time is not available, suggest alternative slots.
                8. Don't give medical advice. Recommend booking an appointment for symptoms.
                
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

    /**
     * ============================================================
     * FALLBACK
     * ============================================================
     */
    private String getFallbackResponse(Doctor doctor) {

        return String.format(
                "Dr. %s is available %s. Fee: ₹%s. Would you like to book an appointment?",

                doctor.getFullName(),

                doctor.getConsultationHours(),

                doctor.getConsultationFee()
        );
    }
}