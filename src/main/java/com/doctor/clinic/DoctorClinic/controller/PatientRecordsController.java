package com.doctor.clinic.DoctorClinic.controller;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.PatientChatMessage;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientChatMessageRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.response.PatientHistoryResponse;
import com.doctor.clinic.DoctorClinic.response.PatientHistoryResponse.AppointmentRecord;
import com.doctor.clinic.DoctorClinic.response.PatientHistoryResponse.ChatRecord;
import com.doctor.clinic.DoctorClinic.response.PatientHistoryResponse.PatientProfile;
import com.doctor.clinic.DoctorClinic.serviceImpl.PatientProfileService;

@RestController
@RequestMapping("/api/patients")
public class PatientRecordsController {

    private final PatientRepo patientRepo;
    private final AppointmentRepo appointmentRepo;
    private final PatientChatMessageRepo chatMessageRepo;

    public PatientRecordsController(PatientRepo patientRepo, AppointmentRepo appointmentRepo,
                                    PatientChatMessageRepo chatMessageRepo) {
        this.patientRepo = patientRepo;
        this.appointmentRepo = appointmentRepo;
        this.chatMessageRepo = chatMessageRepo;
    }

    @GetMapping
    public List<PatientProfile> listPatients(@RequestParam(required = false) String search) {
        String normalizedSearch = search == null || search.isBlank() ? null : search.trim();
        String digits = normalizedSearch == null ? null : PatientProfileService.normalizePhone(normalizedSearch);
        if (digits != null && digits.isBlank()) digits = null;
        return patientRepo.searchByOrganization(organizationId(), normalizedSearch, digits)
                .stream().map(this::toProfile).toList();
    }

    @GetMapping("/{patientId}/history")
    public PatientHistoryResponse patientHistory(@PathVariable Long patientId) {
        Long organizationId = organizationId();
        Patient patient = patientRepo.findByIdAndOrganizationId(patientId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Patient not found"));

        List<AppointmentRecord> appointments = appointmentRepo
                .findPatientHistory(organizationId, patientId, phoneAliases(patient.getNormalizedPhone()))
                .stream().map(this::toAppointmentRecord).toList();
        List<ChatRecord> conversations = chatMessageRepo.findTimelineByPatientId(patientId)
                .stream().map(row -> new ChatRecord(row.getId(), row.getDirection(), row.getMessageType(),
                        row.getMessageText(), row.getAttachmentMimeType(), row.getAttachmentName(),
                        row.getHasAttachment(), row.getCreatedAt(), row.getHasAttachment()
                                ? "/api/patients/" + patientId + "/messages/" + row.getId() + "/attachment"
                                : null)).toList();

        return new PatientHistoryResponse(toProfile(patient), appointments, conversations);
    }

    @GetMapping("/{patientId}/messages/{messageId}/attachment")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long patientId, @PathVariable Long messageId) {
        PatientChatMessage message = chatMessageRepo
                .findAttachmentForOrganization(messageId, patientId, organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found"));
        if (message.getAttachmentData() == null || message.getAttachmentData().length == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Attachment not found");
        }

        MediaType mediaType = MediaType.APPLICATION_OCTET_STREAM;
        try {
            if (message.getAttachmentMimeType() != null) {
                mediaType = MediaType.parseMediaType(message.getAttachmentMimeType());
            }
        } catch (IllegalArgumentException ignored) {
            // Fall back to a safe binary content type for invalid stored metadata.
        }
        String fileName = message.getAttachmentName() == null || message.getAttachmentName().isBlank()
                ? "patient-attachment" : message.getAttachmentName().replaceAll("[\\r\\n\\\"]", "_");
        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName).build().toString())
                .body(message.getAttachmentData());
    }

    private Long organizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Number number) return number.longValue();
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Organization is missing from access token");
    }

    private PatientProfile toProfile(Patient patient) {
        return new PatientProfile(patient.getId(), patient.getFullName(), patient.getNormalizedPhone(),
                patient.getEmail(), patient.getAge(), patient.getGender());
    }

    private AppointmentRecord toAppointmentRecord(Appointment appointment) {
        String doctorName = appointment.getDoctor() == null ? null : appointment.getDoctor().getFullName();
        return new AppointmentRecord(appointment.getId(), doctorName, appointment.getAppointmentDate(),
                appointment.getAppointmentTime(), appointment.getAppointmentStatus(), appointment.getReasonForVisit(),
                appointment.getSymptoms(), appointment.getNotes(), appointment.getConsultationFee(),
                appointment.getPaymentStatus());
    }

    private List<String> phoneAliases(String normalizedPhone) {
        Set<String> aliases = new LinkedHashSet<>();
        aliases.add(normalizedPhone);
        aliases.add("91" + normalizedPhone);
        aliases.add("+91" + normalizedPhone);
        aliases.add("0" + normalizedPhone);
        return new ArrayList<>(aliases);
    }
}
