package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.time.LocalDate;
import java.time.LocalTime;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.doctor.clinic.DoctorClinic.AIServices.GeminiServiceLatest;
import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppConversation;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppMessage;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppResponseRule;
import com.doctor.clinic.DoctorClinic.model.AutomationMode;
import com.doctor.clinic.DoctorClinic.model.ConversationStatus;
import com.doctor.clinic.DoctorClinic.model.MessageDirection;
import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.model.Intent;
import com.doctor.clinic.DoctorClinic.model.ResponseMode;
import com.doctor.clinic.DoctorClinic.model.ResponseRuleTriggerType;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppConversationRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppMessageRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppResponseRuleRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;
import com.doctor.clinic.DoctorClinic.service.WhatsAppAutomationService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WhatsAppAutomationServiceImpl implements WhatsAppAutomationService {

    private final DoctorRepo doctorRepo;
    private final PatientRepo patientRepo;
    private final WhatsAppConversationRepo conversationRepo;
    private final WhatsAppMessageRepo whatsappMessageRepo;
    private final WhatsAppResponseRuleRepo responseRuleRepo;
    private final WhatsappServiceImpl whatsappService;
    private final GeminiServiceLatest geminiServiceLatest;
    private final IntentDetector intentDetector;
    private final AppointmentBookingChatService appointmentBookingChatService;
    private final WhatsAppConversationServiceImpl conversationService;
    private final BookingRequestService bookingRequestService;
    private final AppointmentOverflowService appointmentOverflowService;
    private final ClinicMessageScopeDetector clinicMessageScopeDetector;
    private final CloudflareR2StorageService r2StorageService;
    private final PatientChatHistoryService patientChatHistoryService;
    private final AppointmentRepo appointmentRepo;
    private final DoctorSlotRepo doctorSlotRepo;
    private final AppointmentService appointmentService;

    public WhatsAppAutomationServiceImpl(DoctorRepo doctorRepo,
                                        PatientRepo patientRepo,
                                        WhatsAppConversationRepo conversationRepo,
                                        WhatsAppMessageRepo whatsappMessageRepo,
                                        WhatsAppResponseRuleRepo responseRuleRepo,
                                        WhatsappServiceImpl whatsappService,
                                        GeminiServiceLatest geminiServiceLatest,
                                        IntentDetector intentDetector,
                                        AppointmentBookingChatService appointmentBookingChatService,
                                        WhatsAppConversationServiceImpl conversationService,
                                        BookingRequestService bookingRequestService,
                                        AppointmentOverflowService appointmentOverflowService,
                                        ClinicMessageScopeDetector clinicMessageScopeDetector,
                                        CloudflareR2StorageService r2StorageService,
                                        PatientChatHistoryService patientChatHistoryService,
                                        AppointmentRepo appointmentRepo,
                                        DoctorSlotRepo doctorSlotRepo,
                                        AppointmentService appointmentService) {
        this.doctorRepo = doctorRepo;
        this.patientRepo = patientRepo;
        this.conversationRepo = conversationRepo;
        this.whatsappMessageRepo = whatsappMessageRepo;
        this.responseRuleRepo = responseRuleRepo;
        this.whatsappService = whatsappService;
        this.geminiServiceLatest = geminiServiceLatest;
        this.intentDetector = intentDetector;
        this.appointmentBookingChatService = appointmentBookingChatService;
        this.conversationService = conversationService;
        this.bookingRequestService = bookingRequestService;
        this.appointmentOverflowService = appointmentOverflowService;
        this.clinicMessageScopeDetector = clinicMessageScopeDetector;
        this.r2StorageService = r2StorageService;
        this.patientChatHistoryService = patientChatHistoryService;
        this.appointmentRepo = appointmentRepo;
        this.doctorSlotRepo = doctorSlotRepo;
        this.appointmentService = appointmentService;
    }

    @Override
    @Async("whatsappTaskExecutor")
    public void processInboundAsync(Long organizationId, Long doctorId, Long patientId, String whatsappMessageId, String phoneNumber,
                                   String messageText, String messageType, String mediaId, String mimeType, String attachmentName,
                                   String selectionId) {
        try {
            Doctor doctor = doctorRepo.findById(doctorId).orElse(null);
            if (doctor == null || !doctor.getOrganization().getId().equals(organizationId)) {
                log.warn("Skipping WhatsApp automation for unknown doctor {} in org {}", doctorId, organizationId);
                return;
            }

            if (samePhone(doctor.getPhoneNumber(), phoneNumber)) {
                String decision = appointmentOverflowService.handleWhatsAppDecision(doctorId, selectionId, messageText);
                if (decision != null) {
                    whatsappService.sendMessage(doctor, phoneNumber, decision);
                }
                return;
            }

            Patient patient = patientRepo.findById(patientId).orElse(null);
            if (patient == null || !patient.getOrganization().getId().equals(organizationId)) {
                log.warn("Skipping WhatsApp automation for unknown patient {} in org {}", patientId, organizationId);
                return;
            }

            WhatsAppConversation conversation = conversationRepo
                    .findByOrganizationIdAndPatientIdAndPhoneNumber(organizationId, patient.getId(), phoneNumber)
                    .orElseGet(() -> conversationService.upsertConversation(organizationId, patient, phoneNumber, doctor));

            selectionId = recoverAppointmentSelectionId(organizationId, doctor, patient, conversation,
                    messageText, selectionId);
            if ("interactive".equalsIgnoreCase(messageType)) {
                log.info("WhatsApp interactive reply received: orgId={} conversationId={} selectionId={}",
                        organizationId, conversation.getId(), selectionId);
            }

            if (conversation.getStatus() == ConversationStatus.CLOSED && !conversation.isHumanTakeover()) {
                conversation = conversationService.reopenConversation(conversation.getId(), organizationId);
            }

            boolean automationEnabled = conversation.getStatus() != ConversationStatus.CLOSED
                    && conversation.isAiEnabled() && !conversation.isHumanTakeover();
            boolean appointmentAction = isAppointmentAction(messageText, selectionId);
            boolean reportsAction = "MENU_REPORTS".equals(selectionId);
            boolean reportCandidate = mediaId != null
                    && ("image".equalsIgnoreCase(messageType) || "document".equalsIgnoreCase(messageType));
            if (!automationEnabled && !reportCandidate && !appointmentAction && !reportsAction) {
                log.info("Automation disabled for conversation {} with status {}", conversation.getId(), conversation.getStatus());
                return;
            }

            if (reportsAction) {
                boolean sent = conversationService.sendSavedMedicalReports(
                        organizationId, patient, doctor, phoneNumber);
                log.info("Saved medical reports sent from WhatsApp selection: conversationId={} sent={}",
                        conversation.getId(), sent);
                return;
            }

            Map<String, Object> interactive = new HashMap<>();
            String response;
            byte[] reportBytes = null;
            String reportMimeType = null;
            if (mediaId != null && ("image".equalsIgnoreCase(messageType) || "document".equalsIgnoreCase(messageType))) {
                try {
                    WhatsappServiceImpl.DownloadedMedia downloaded = whatsappService.downloadMedia(mediaId, doctor.getWhatsappAccessToken());
                    reportBytes = downloaded.bytes();
                    reportMimeType = downloaded.mimeType();
                    log.info("Downloaded WhatsApp attachment: orgId={} type={} mimeType={} bytes={}",
                            organizationId, messageType, reportMimeType, reportBytes.length);
                    if (!hasSupportedFileSignature(reportBytes, reportMimeType)) {
                        response = automationEnabled ? "I can save medical reports sent as a PDF or a clear JPEG, PNG, or WebP image. This file type or file content isn't supported, so it wasn't saved." : null;
                    } else if (!geminiServiceLatest.isValidMedicalReport(reportBytes, reportMimeType)) {
                        log.info("Attachment classified as not a medical report: orgId={} mimeType={}", organizationId, reportMimeType);
                        response = automationEnabled ? "I couldn't verify this file as a medical report, so it wasn't saved. Please send a clear image or PDF of a lab result, scan, prescription, discharge summary, or similar medical document." : null;
                    } else {
                        log.info("Attachment classified as a medical report: orgId={} mimeType={}", organizationId, reportMimeType);
                        CloudflareR2StorageService.StoredFile stored = r2StorageService.storeMedicalReport(
                                organizationId, patient.getId(), doctor.getId(), reportBytes, reportMimeType, attachmentName);
                        log.info("Medical report stored in R2: orgId={} bytes={}", organizationId, stored.size());
                        conversationService.attachMedicalReport(organizationId, whatsappMessageId, stored);
                        patientChatHistoryService.attachMedicalReport(organizationId, whatsappMessageId, stored);
                        if (automationEnabled) {
                            String reportPrompt = messageText == null || messageText.isBlank()
                                    ? "Please review the medical report I attached. Explain the visible report content in simple terms."
                                    : messageText;
                            response = resolveResponse(doctor, patient, conversation, reportPrompt, messageType,
                                    null, reportMimeType, attachmentName, selectionId, interactive, reportBytes, reportMimeType);
                        } else {
                            response = null;
                        }
                    }
                } catch (Exception e) {
                    log.warn("Medical report attachment processing failed for orgId={} patientId={}: {}",
                            organizationId, patientId, e.getMessage(), e);
                    response = automationEnabled ? "I couldn't securely save or review that file right now. It wasn't saved; please try again later or contact the clinic." : null;
                }
            } else {
                response = resolveResponse(doctor, patient, conversation, messageText, messageType, mediaId, mimeType,
                        attachmentName, selectionId, interactive, null, null);
            }
            if (response == null || response.isBlank()) {
                log.info("No outbound response generated for conversation {}", conversation.getId());
                return;
            }

            boolean sent = appointmentAction && !automationEnabled
                    ? conversationService.sendSystemNotification(organizationId, patient, doctor, phoneNumber,
                            response, interactive.isEmpty() ? null : interactive)
                    : conversationService.sendAutomatedMessage(
                            organizationId, patient, doctor, phoneNumber, response,
                            interactive.isEmpty() ? null : interactive);
            if (sent) {
                log.info("Outbound WhatsApp response sent for conversation {}", conversation.getId());
            } else {
                log.warn("Failed to send outbound WhatsApp response for conversation {}", conversation.getId());
            }
        } catch (Exception ex) {
            log.error("WhatsApp automation processing failed for orgId={} doctorId={} patientId={} phoneNumber={}",
                    organizationId, doctorId, patientId, phoneNumber, ex);
        }
    }

    private boolean samePhone(String first, String second) {
        if (first == null || second == null) return false;
        String a = first.replaceAll("\\D", "");
        String b = second.replaceAll("\\D", "");
        if (a.length() == 10) a = "91" + a;
        if (b.length() == 10) b = "91" + b;
        return a.equals(b);
    }

    private boolean isAppointmentAction(String messageText, String selectionId) {
        String selected = selectionId == null ? "" : selectionId;
        if (selected.startsWith("APPT_CANCEL_") || selected.startsWith("APPT_RESCHEDULE_")
                || "MENU_CANCEL_APPOINTMENT".equals(selected)
                || "MENU_RESCHEDULE_APPOINTMENT".equals(selected)
                || "MENU_MY_APPOINTMENTS".equals(selected)) {
            return true;
        }
        String normalized = messageText == null ? "" : messageText.toLowerCase(Locale.ROOT);
        return normalized.contains("appointment")
                && (normalized.contains("cancel") || normalized.contains("reschedul")
                    || normalized.contains("my appointment") || normalized.contains("upcoming appointment"));
    }

    private String recoverAppointmentSelectionId(Long organizationId, Doctor doctor, Patient patient,
                                                  WhatsAppConversation conversation, String replyTitle,
                                                  String selectionId) {
        if (selectionId != null && (selectionId.startsWith("APPT_CANCEL_")
                || selectionId.startsWith("APPT_RESCHEDULE_"))) return selectionId;
        if (replyTitle == null || replyTitle.isBlank()) return selectionId;

        WhatsAppMessage lastOutbound = whatsappMessageRepo
                .findTopByOrganizationIdAndConversationIdAndDirectionOrderByCreatedAtDesc(
                        organizationId, conversation.getId(), MessageDirection.OUTBOUND)
                .orElse(null);
        if (lastOutbound == null || lastOutbound.getContent() == null) return selectionId;
        String prompt = lastOutbound.getContent().toLowerCase(Locale.ROOT);
        String actionPrefix;
        if (prompt.contains("choose the appointment you'd like to cancel")) {
            actionPrefix = "APPT_CANCEL_SELECT_";
        } else if (prompt.contains("choose the appointment you'd like to reschedule")) {
            actionPrefix = "APPT_RESCHEDULE_SELECT_";
        } else {
            return selectionId;
        }

        String normalizedReply = replyTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        List<String> aliases = patientPhoneAliases(patient);
        String recovered = upcomingAppointments(organizationId, patient, doctor, aliases).stream()
                .filter(appointment -> {
                    String rowTitle = appointment.getAppointmentDate().format(
                            java.time.format.DateTimeFormatter.ofPattern("dd MMM")) + " "
                            + appointment.getAppointmentTime().format(
                                    java.time.format.DateTimeFormatter.ofPattern("h:mm a"));
                    return normalizedReply.contains(rowTitle.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", ""));
                })
                .findFirst()
                .map(appointment -> actionPrefix + appointment.getId())
                .orElse(selectionId);
        if (!java.util.Objects.equals(recovered, selectionId)) {
            log.warn("Recovered missing appointment list selection from reply title: conversationId={} selectionId={}",
                    conversation.getId(), recovered);
        }
        return recovered;
    }

    private String resolveResponse(Doctor doctor, Patient patient, WhatsAppConversation conversation, String messageText,
                                  String messageType, String mediaId, String mimeType, String attachmentName,
                                  String selectionId, Map<String, Object> interactiveOut,
                                  byte[] mediaBytes, String mediaContentType) {
        Optional<AppointmentBookingChatService.Reply> appointmentAction = appointmentActionReply(
                doctor, patient, conversation, messageText, selectionId);
        if (appointmentAction.isPresent()) {
            if (appointmentAction.get().interactive() != null) interactiveOut.putAll(appointmentAction.get().interactive());
            return appointmentAction.get().text();
        }

        if (selectionId != null && selectionId.startsWith("MENU_")) {
            switch (selectionId) {
                case "MENU_BOOK_APPOINTMENT" -> {
                    Optional<AppointmentBookingChatService.Reply> booking = appointmentBookingChatService
                            .handle(doctor, patient, "book appointment", null);
                    if (booking.isPresent()) {
                        if (booking.get().interactive() != null) interactiveOut.putAll(booking.get().interactive());
                        return booking.get().text();
                    }
                }
                case "MENU_MY_APPOINTMENTS" -> { return myAppointmentsReply(doctor, patient, conversation); }
                case "MENU_ASK_HEALTH" -> { return "Please type your health question, and I’ll do my best to help. For urgent or severe symptoms, contact emergency services or the clinic directly."; }
                case "MENU_REPORTS" -> { return "Medical reports and prescriptions you send here are saved securely to your patient record. Please contact clinic staff if you need help accessing them."; }
                case "MENU_CLINIC_INFO" -> { return clinicInformationReply(doctor); }
                case "MENU_TALK_TO_STAFF" -> {
                    conversation.setHumanTakeover(true);
                    conversation.setAiEnabled(false);
                    conversation.setAutomationMode(AutomationMode.MANUAL);
                    conversation.setStatus(ConversationStatus.MANUAL);
                    conversationRepo.save(conversation);
                    return "I’ve notified the clinic team. A staff member will follow up with you here.";
                }
                default -> { return greetingMenuText(); }
            }
        }

        String normalized = normalize(messageText);
        if (normalized == null || normalized.isBlank()) {
            return null;
        }

        if (selectionId != null && selectionId.startsWith("offer_")) {
            String[] parts = selectionId.split("_");
            if (parts.length == 3) {
                try {
                    return bookingRequestService.confirmOffer(Long.parseLong(parts[1]), Long.parseLong(parts[2]));
                } catch (NumberFormatException ignored) {
                    // fall through to normal handling below
                }
            }
        }

        Optional<AppointmentBookingChatService.Reply> bookingReply = appointmentBookingChatService.handle(doctor, patient, normalized, selectionId);
        if (bookingReply.isPresent()) {
            if (bookingReply.get().interactive() != null) {
                interactiveOut.putAll(bookingReply.get().interactive());
            }
            return bookingReply.get().text();
        }

        if (!clinicMessageScopeDetector.isInScope(normalized)) {
            return "I can help with health questions and Dr. " + doctor.getFullName()
                    + "’s clinic services, such as appointments, fees, clinic hours, and patient records. "
                    + "I can’t help with unrelated requests. What clinic-related question can I help with?";
        }

        WhatsAppResponseRule rule = findMatchingRule(doctor, conversation.getOrganization(), normalized);
        if (rule != null && rule.getResponseMode() == ResponseMode.TEXT) {
            log.info("Matched response rule {} for conversation {}", rule.getName(), conversation.getId());
            return rule.getResponseText();
        }

        if (rule != null && rule.getResponseMode() == ResponseMode.ESCALATE) {
            return "Thanks for reaching out. Our team has been notified and will get back to you shortly.";
        }

        String keywordResponse = resolveKeywordResponse(doctor, normalized);
        if (keywordResponse != null) {
            return keywordResponse;
        }

        Intent intent = intentDetector.detect(normalized);
        if (intent == Intent.GREETING || intent == Intent.THANKS || intent == Intent.GOODBYE) {
            return switch (intent) {
                case GREETING -> {
                    interactiveOut.putAll(greetingMenu(doctor));
                    yield greetingMenuText();
                }
                case THANKS -> "You’re welcome! Is there anything else I can help you with?";
                case GOODBYE -> "Thank you for contacting the clinic. Have a great day!";
                default -> null;
            };
        }

        String recentHistory = "";
        return geminiServiceLatest.generateResponse(normalized, doctor, recentHistory, mediaBytes, mediaContentType);
    }

    private Optional<AppointmentBookingChatService.Reply> appointmentActionReply(
            Doctor doctor, Patient patient, WhatsAppConversation conversation, String messageText, String selectionId) {
        String selected = selectionId == null ? "" : selectionId;
        String normalized = messageText == null ? "" : messageText.trim().toLowerCase(Locale.ROOT);
        Long organizationId = conversation.getOrganization().getId();
        List<String> phoneAliases = patientPhoneAliases(patient);

        if (selected.startsWith("APPT_CANCEL_ABORT") || selected.startsWith("APPT_RESCHEDULE_ABORT")) {
            return Optional.of(new AppointmentBookingChatService.Reply("Okay, I left your appointment unchanged.", null));
        }
        if (selected.startsWith("APPT_CANCEL_SELECT_")) {
            Long id = parseAppointmentId(selected.substring("APPT_CANCEL_SELECT_".length()));
            Appointment appointment = findUpcomingAppointment(id, organizationId, patient, doctor, phoneAliases);
            if (appointment == null) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't find that upcoming appointment. Please choose an appointment again.", null));
            try {
                appointmentService.cancelPatientAppointment(organizationId, patient.getId(), phoneAliases, id,
                        "Cancelled by patient via WhatsApp");
                log.info("WhatsApp appointment cancelled after patient selected it: orgId={} appointmentId={}",
                        organizationId, id);
                return Optional.of(new AppointmentBookingChatService.Reply("Your appointment with Dr. "
                        + doctor.getFullName() + " on " + appointment.getAppointmentDate() + " at "
                        + appointment.getAppointmentTime() + " has been cancelled.", null));
            } catch (RuntimeException ex) {
                log.warn("WhatsApp patient cancellation failed: orgId={} appointmentId={} reason={}",
                        organizationId, id, ex.getMessage());
                return Optional.of(new AppointmentBookingChatService.Reply("I couldn't cancel that appointment. "
                        + "It may have already started or changed. Please contact the clinic for help.", null));
            }
        }
        if (selected.startsWith("APPT_CANCEL_CONFIRM_")) {
            Long id = parseAppointmentId(selected.substring("APPT_CANCEL_CONFIRM_".length()));
            if (id == null) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't identify that appointment. Please try again.", null));
            try {
                appointmentService.cancelPatientAppointment(organizationId, patient.getId(), phoneAliases, id,
                        "Cancelled by patient via WhatsApp");
                return Optional.of(new AppointmentBookingChatService.Reply("Your appointment has been cancelled.", null));
            } catch (RuntimeException ex) {
                log.info("WhatsApp patient cancellation could not complete for appointment {}: {}", id, ex.getMessage());
                return Optional.of(new AppointmentBookingChatService.Reply("I couldn't cancel that appointment. It may have already started or changed. Please contact the clinic for help.", null));
            }
        }
        if (selected.startsWith("APPT_RESCHEDULE_SELECT_")) {
            Long id = parseAppointmentId(selected.substring("APPT_RESCHEDULE_SELECT_".length()));
            Appointment appointment = findUpcomingAppointment(id, organizationId, patient, doctor, phoneAliases);
            if (appointment == null) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't find that upcoming appointment. Please choose an appointment again.", null));
            return Optional.of(rescheduleDateChoices(appointment, doctor));
        }
        if (selected.startsWith("APPT_RESCHEDULE_DATE_")) {
            String[] parts = selected.substring("APPT_RESCHEDULE_DATE_".length()).split("_", 2);
            if (parts.length != 2) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't read that date. Please start rescheduling again.", null));
            Long id = parseAppointmentId(parts[0]);
            try {
                LocalDate date = LocalDate.parse(parts[1]);
                Appointment appointment = findUpcomingAppointment(id, organizationId, patient, doctor, phoneAliases);
                if (appointment == null) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't find that upcoming appointment. Please start again.", null));
                return Optional.of(rescheduleTimeChoices(appointment, doctor, date));
            } catch (RuntimeException ex) {
                return Optional.of(new AppointmentBookingChatService.Reply("I couldn't read that date. Please start rescheduling again.", null));
            }
        }
        if (selected.startsWith("APPT_RESCHEDULE_TIME_")) {
            String value = selected.substring("APPT_RESCHEDULE_TIME_".length());
            int idEnd = value.indexOf('_');
            int dateEnd = idEnd < 0 ? -1 : value.indexOf('_', idEnd + 1);
            if (idEnd < 0 || dateEnd < 0) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't read that time. Please choose an available time again.", null));
            Long id = parseAppointmentId(value.substring(0, idEnd));
            try {
                LocalDate date = LocalDate.parse(value.substring(idEnd + 1, dateEnd));
                LocalTime time = LocalTime.parse(value.substring(dateEnd + 1));
                Appointment appointment = findUpcomingAppointment(id, organizationId, patient, doctor, phoneAliases);
                if (appointment == null) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't find that upcoming appointment. Please start again.", null));
                String body = "Reschedule your appointment with Dr. " + doctor.getFullName() + " to " + date + " at " + time + "?";
                return Optional.of(new AppointmentBookingChatService.Reply(body, appointmentButtons(body,
                        button("APPT_RESCHEDULE_CONFIRM_" + id + "_" + date + "_" + time, "Confirm time"),
                        button("APPT_RESCHEDULE_ABORT", "Keep current time"))));
            } catch (RuntimeException ex) {
                return Optional.of(new AppointmentBookingChatService.Reply("I couldn't read that time. Please choose an available time again.", null));
            }
        }
        if (selected.startsWith("APPT_RESCHEDULE_CONFIRM_")) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile(
                    "^APPT_RESCHEDULE_CONFIRM_(\\d+)_(\\d{4}-\\d{2}-\\d{2})_(\\d{2}:\\d{2}(?::\\d{2})?)$").matcher(selected);
            if (!matcher.matches()) return Optional.of(new AppointmentBookingChatService.Reply("I couldn't read that time. Please start rescheduling again.", null));
            Long id = parseAppointmentId(matcher.group(1));
            try {
                var moved = appointmentService.reschedulePatientAppointment(organizationId, patient.getId(), phoneAliases,
                        id, LocalDate.parse(matcher.group(2)), LocalTime.parse(matcher.group(3)));
                return Optional.of(new AppointmentBookingChatService.Reply("Your previous appointment has been cancelled, and your new appointment is scheduled for "
                        + moved.getAppointmentDate() + " at " + moved.getAppointmentTime() + ". Please contact the clinic about payment confirmation.", null));
            } catch (RuntimeException ex) {
                log.info("WhatsApp patient reschedule could not complete for appointment {}: {}", id, ex.getMessage());
                return Optional.of(new AppointmentBookingChatService.Reply("That time is no longer available or this appointment can't be changed. Please start again and choose another time, or contact the clinic.", null));
            }
        }

        boolean cancelRequested = "MENU_CANCEL_APPOINTMENT".equals(selected)
                || (normalized.contains("cancel") && normalized.contains("appointment"));
        boolean rescheduleRequested = "MENU_RESCHEDULE_APPOINTMENT".equals(selected)
                || (normalized.contains("appointment") && (normalized.contains("reschedul") || normalized.contains("change the time")));
        if (cancelRequested || rescheduleRequested) {
            List<Appointment> appointments = upcomingAppointments(organizationId, patient, doctor, phoneAliases);
            if (appointments.isEmpty()) return Optional.of(new AppointmentBookingChatService.Reply(
                    "You don't have an upcoming appointment with Dr. " + doctor.getFullName() + " to "
                            + (cancelRequested ? "cancel." : "reschedule."), null));
            String action = cancelRequested ? "cancel" : "reschedule";
            String body = "Choose the appointment you'd like to " + action + ".";
            List<Map<String, Object>> rows = appointments.stream().limit(10).map(appointment -> menuRow(
                    (cancelRequested ? "APPT_CANCEL_SELECT_" : "APPT_RESCHEDULE_SELECT_") + appointment.getId(),
                    appointment.getAppointmentDate().format(java.time.format.DateTimeFormatter.ofPattern("dd MMM"))
                            + " " + appointment.getAppointmentTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")),
                    appointment.getAppointmentStatus() + " with Dr. " + doctor.getFullName())).toList();
            Map<String, Object> list = Map.of("type", "list", "body", Map.of("text", body),
                    "action", Map.of("button", "Choose appointment", "sections", List.of(Map.of("title", "Upcoming appointments", "rows", rows))));
            return Optional.of(new AppointmentBookingChatService.Reply(body, list));
        }
        return Optional.empty();
    }

    private AppointmentBookingChatService.Reply rescheduleDateChoices(Appointment appointment, Doctor doctor) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();
        java.util.TreeSet<java.time.LocalDate> dates = new java.util.TreeSet<>();
        doctorSlotRepo.findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(doctor.getId(), today, today.plusDays(30))
                .stream().filter(com.doctor.clinic.DoctorClinic.entity.DoctorSlot::hasAvailability)
                .filter(slot -> slot.getSlotDate().isAfter(today) || slot.getStartTime().isAfter(now))
                .map(com.doctor.clinic.DoctorClinic.entity.DoctorSlot::getSlotDate)
                .filter(date -> !date.equals(appointment.getAppointmentDate())).forEach(dates::add);
        if (dates.isEmpty()) return new AppointmentBookingChatService.Reply("There are no other available times in the next 30 days. Please contact the clinic to reschedule.", null);
        List<Map<String, Object>> rows = dates.stream().limit(10).map(date -> menuRow(
                "APPT_RESCHEDULE_DATE_" + appointment.getId() + "_" + date,
                date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, dd MMM")), "Available appointment times")).toList();
        String body = "Choose a new date for your appointment with Dr. " + doctor.getFullName() + ".";
        return new AppointmentBookingChatService.Reply(body, Map.of("type", "list", "body", Map.of("text", body),
                "action", Map.of("button", "Choose date", "sections", List.of(Map.of("title", "Available dates", "rows", rows)))));
    }

    private AppointmentBookingChatService.Reply rescheduleTimeChoices(Appointment appointment, Doctor doctor, java.time.LocalDate date) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();
        List<Map<String, Object>> rows = doctorSlotRepo.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctor.getId(), date).stream()
                .filter(com.doctor.clinic.DoctorClinic.entity.DoctorSlot::hasAvailability)
                .filter(slot -> date.isAfter(today) || slot.getStartTime().isAfter(now))
                .filter(slot -> !date.equals(appointment.getAppointmentDate()) || !slot.getStartTime().equals(appointment.getAppointmentTime()))
                .sorted(java.util.Comparator.comparing(com.doctor.clinic.DoctorClinic.entity.DoctorSlot::getStartTime))
                .limit(10).map(slot -> menuRow("APPT_RESCHEDULE_TIME_" + appointment.getId() + "_" + date + "_" + slot.getStartTime(),
                        slot.getStartTime().format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")), "Available" )).toList();
        if (rows.isEmpty()) return rescheduleDateChoices(appointment, doctor);
        String body = "Choose a new time on " + date + ".";
        return new AppointmentBookingChatService.Reply(body, Map.of("type", "list", "body", Map.of("text", body),
                "action", Map.of("button", "Choose time", "sections", List.of(Map.of("title", "Available times", "rows", rows)))));
    }

    private Map<String, Object> appointmentButtons(String body, Map<String, Object> first, Map<String, Object> second) {
        return Map.of("type", "button", "body", Map.of("text", body), "action", Map.of("buttons", List.of(first, second)));
    }

    private Map<String, Object> button(String id, String title) {
        return Map.of("type", "reply", "reply", Map.of("id", id, "title", title));
    }

    private List<Appointment> upcomingAppointments(Long organizationId, Patient patient, Doctor doctor, List<String> aliases) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalTime now = java.time.LocalTime.now();
        return appointmentRepo.findPatientHistory(organizationId, patient.getId(), aliases).stream()
                .filter(appointment -> appointment.getDoctor() != null && appointment.getDoctor().getId().equals(doctor.getId()))
                .filter(appointment -> "SCHEDULED".equalsIgnoreCase(appointment.getAppointmentStatus())
                        || "CONFIRMED".equalsIgnoreCase(appointment.getAppointmentStatus()))
                .filter(appointment -> appointment.getAppointmentDate().isAfter(today)
                        || (appointment.getAppointmentDate().equals(today) && appointment.getAppointmentTime().isAfter(now)))
                .sorted(java.util.Comparator.comparing(Appointment::getAppointmentDate).thenComparing(Appointment::getAppointmentTime))
                .toList();
    }

    private Appointment findUpcomingAppointment(Long id, Long organizationId, Patient patient, Doctor doctor, List<String> aliases) {
        if (id == null) return null;
        return appointmentRepo.findForPatientAction(id, organizationId, patient.getId(), aliases).filter(appointment ->
                appointment.getDoctor() != null && appointment.getDoctor().getId().equals(doctor.getId())
                        && ("SCHEDULED".equalsIgnoreCase(appointment.getAppointmentStatus())
                        || "CONFIRMED".equalsIgnoreCase(appointment.getAppointmentStatus()))
                        && (appointment.getAppointmentDate().isAfter(java.time.LocalDate.now())
                        || (appointment.getAppointmentDate().equals(java.time.LocalDate.now())
                        && appointment.getAppointmentTime().isAfter(java.time.LocalTime.now())))).orElse(null);
    }

    private List<String> patientPhoneAliases(Patient patient) {
        String phone = patient.getNormalizedPhone();
        return List.of(phone, "91" + phone, "+91" + phone, "0" + phone);
    }

    private Long parseAppointmentId(String value) {
        try { return Long.parseLong(value); } catch (Exception ex) { return null; }
    }

    private String greetingMenuText() {
        return "Hi! How can we help you today? Choose an option or type your question.";
    }

    private Map<String, Object> greetingMenu(Doctor doctor) {
        String title = "ARCHSTER HEALTH - CHATBOT MENU";
        List<Map<String, Object>> rows = List.of(
                menuRow("MENU_BOOK_APPOINTMENT", "Book an appointment", "Find a doctor and available time"),
                menuRow("MENU_MY_APPOINTMENTS", "My appointments", "View upcoming and cancelled appointments"),
                menuRow("MENU_ASK_HEALTH", "Ask a health question", "General health information and guidance"),
                menuRow("MENU_REPORTS", "Reports & prescriptions", "Access medical documents securely"),
                menuRow("MENU_CLINIC_INFO", "Clinic information", "Fees, timings, location and services"),
                menuRow("MENU_TALK_TO_STAFF", "Talk to clinic staff", "Request human assistance"),
                menuRow("MENU_CANCEL_APPOINTMENT", "Cancel appointment", "Cancel an upcoming visit"),
                menuRow("MENU_RESCHEDULE_APPOINTMENT", "Reschedule appointment", "Choose a different date and time"));
        return Map.of(
                "type", "list",
                "header", Map.of("type", "text", "text", title),
                "body", Map.of("text", greetingMenuText()),
                "footer", Map.of("text", "Dr. " + doctor.getFullName() + "'s clinic"),
                "action", Map.of("button", "View options", "sections", List.of(Map.of("title", "How can we help?", "rows", rows))));
    }

    private Map<String, Object> menuRow(String id, String title, String description) {
        return Map.of("id", id, "title", title, "description", description);
    }

    private String clinicInformationReply(Doctor doctor) {
        String specialization = doctor.getSpecialization() == null ? "Medical consultation" : doctor.getSpecialization();
        String hours = doctor.getConsultationHours() == null ? "Please contact the clinic for timings" : doctor.getConsultationHours();
        String fee = doctor.getConsultationFee() == null ? "Please contact the clinic for the consultation fee" : "₹" + doctor.getConsultationFee();
        return "Dr. " + doctor.getFullName() + " - " + specialization + "\nClinic hours: " + hours + "\nConsultation fee: " + fee;
    }

    private String myAppointmentsReply(Doctor doctor, Patient patient, WhatsAppConversation conversation) {
        Long organizationId = conversation.getOrganization().getId();
        List<String> aliases = patientPhoneAliases(patient);
        List<Appointment> upcoming = upcomingAppointments(organizationId, patient, doctor, aliases)
                .stream().limit(5).toList();
        List<Appointment> cancelled = appointmentRepo.findPatientHistory(organizationId, patient.getId(), aliases).stream()
                .filter(appointment -> appointment.getDoctor() != null && appointment.getDoctor().getId().equals(doctor.getId()))
                .filter(appointment -> "CANCELLED".equalsIgnoreCase(appointment.getAppointmentStatus()))
                .sorted(java.util.Comparator.comparing(Appointment::getCancelledAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .limit(5).toList();
        if (upcoming.isEmpty() && cancelled.isEmpty()) {
            return "You don't have any upcoming or recently cancelled appointments with Dr. "
                    + doctor.getFullName() + ". Choose ‘Book an appointment’ if you'd like to schedule one.";
        }

        StringBuilder response = new StringBuilder("Your appointments with Dr. ").append(doctor.getFullName()).append(':');
        if (!upcoming.isEmpty()) {
            response.append("\nUpcoming:");
            upcoming.forEach(appointment -> response.append("\n")
                    .append(appointment.getAppointmentDate()).append(" at ").append(appointment.getAppointmentTime())
                    .append(" - ").append(appointment.getAppointmentStatus()));
        }
        if (!cancelled.isEmpty()) {
            response.append("\nRecently cancelled:");
            cancelled.forEach(appointment -> response.append("\n")
                    .append(appointment.getAppointmentDate()).append(" at ").append(appointment.getAppointmentTime())
                    .append(" - CANCELLED"));
        }
        return response.toString();
    }

    private boolean hasSupportedFileSignature(byte[] bytes, String mimeType) {
        if (bytes == null || bytes.length < 4 || mimeType == null) return false;
        return switch (mimeType.toLowerCase(Locale.ROOT)) {
            case "application/pdf" -> bytes.length >= 5 && bytes[0] == '%'
                    && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F' && bytes[4] == '-';
            case "image/jpeg" -> bytes.length >= 3 && (bytes[0] & 0xff) == 0xff
                    && (bytes[1] & 0xff) == 0xd8 && (bytes[2] & 0xff) == 0xff;
            case "image/png" -> bytes.length >= 8 && (bytes[0] & 0xff) == 0x89 && bytes[1] == 'P'
                    && bytes[2] == 'N' && bytes[3] == 'G' && bytes[4] == 0x0d && bytes[5] == 0x0a
                    && bytes[6] == 0x1a && bytes[7] == 0x0a;
            case "image/webp" -> bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I'
                    && bytes[2] == 'F' && bytes[3] == 'F' && bytes[8] == 'W' && bytes[9] == 'E'
                    && bytes[10] == 'B' && bytes[11] == 'P';
            default -> false;
        };
    }

    private WhatsAppResponseRule findMatchingRule(Doctor doctor, Organization organization, String normalized) {
        List<WhatsAppResponseRule> rules = responseRuleRepo.findByOrganizationIdAndActiveTrueOrderByPriorityDesc(organization.getId());
        if (doctor != null) {
            rules.addAll(responseRuleRepo.findByOrganizationIdAndDoctorIdAndActiveTrueOrderByPriorityDesc(organization.getId(), doctor.getId()));
        }
        rules.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        for (WhatsAppResponseRule rule : rules) {
            if (rule.getTriggerType() == ResponseRuleTriggerType.KEYWORD) {
                if (normalized.contains(rule.getTriggerValue().toLowerCase(Locale.ROOT))) {
                    return rule;
                }
            }
            if (rule.getTriggerType() == ResponseRuleTriggerType.PHRASE) {
                if (normalized.contains(rule.getTriggerValue().toLowerCase(Locale.ROOT))) {
                    return rule;
                }
            }
            if (rule.getTriggerType() == ResponseRuleTriggerType.INTENT) {
                Intent intent = intentDetector.detect(normalized);
                if (intent.name().equalsIgnoreCase(rule.getTriggerValue())) {
                    return rule;
                }
            }
        }
        return null;
    }

    private String resolveKeywordResponse(Doctor doctor, String normalized) {
        if (normalized.contains("consultation fee") || normalized.contains("fee") || normalized.contains("price")) {
            if (doctor.getConsultationFee() != null) {
                return "Consultation fee is ₹" + doctor.getConsultationFee() + ".";
            }
            return "Please contact the clinic for the latest consultation fee details.";
        }

        if (normalized.contains("timing") || normalized.contains("hours") || normalized.contains("open") || normalized.contains("clinic timing")) {
            if (doctor.getConsultationHours() != null && !doctor.getConsultationHours().isBlank()) {
                return "Clinic consultation hours are: " + doctor.getConsultationHours() + ".";
            }
            return "Our clinic is available during standard consultation hours. Please contact the clinic for exact timings.";
        }

        if (normalized.contains("doctor") || normalized.contains("specialist") || normalized.contains("doctor name")) {
            return "You are speaking with Dr. " + doctor.getFullName() + ", a " + (doctor.getSpecialization() == null ? "consultant" : doctor.getSpecialization()) + ".";
        }

        if (normalized.contains("book appointment") || normalized.contains("appointment") || normalized.contains("schedule")) {
            return "Please share your preferred date and time, and I will help check availability for your appointment.";
        }

        return null;
    }

    private String normalize(String message) {
        if (message == null) {
            return "";
        }
        return message.trim().toLowerCase(Locale.ROOT);
    }
}
