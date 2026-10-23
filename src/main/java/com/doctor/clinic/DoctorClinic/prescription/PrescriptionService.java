package com.doctor.clinic.DoctorClinic.prescription;

import com.doctor.clinic.DoctorClinic.repo.*;
import com.doctor.clinic.DoctorClinic.security.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class PrescriptionService {
    private final PrescriptionRepository prescriptions;
    private final PatientRepo patients;
    private final DoctorRepo doctors;
    private final AppointmentRepo appointments;
    private final AuthorizationService authorization;
    private final ObjectMapper mapper;
    private final PrescriptionDocumentSettingsService documentSettings;

    public record Draft(UUID id, long version, Long userId, Long patientId, Long appointmentId,
            UUID replacesId, PrescriptionContent content) {}
    public record View(UUID id, long version, Long userId, Long patientId, Long appointmentId,
            UUID replacesId, String status, PrescriptionContent content, Map<String, Object> identity, Map<String, Object> documentTemplate,
            Instant createdAt, Instant updatedAt, Instant finalizedAt) {}

    @Transactional(readOnly = true)
    public Page<View> list(Long userId, Long patientId, int page) {
        var user = authorization.requireOwnOrganization();
        authorization.requireDoctorPermission("PRESCRIPTIONS:VIEW", user.organizationId(), userId);
        Long doctorId = requireDoctor(userId, user.organizationId()).getId();
        var pageable = PageRequest.of(Math.max(0, page), 20, Sort.by("createdAt").descending());
        return (patientId == null
                ? prescriptions.findByOrganizationIdAndDoctorId(user.organizationId(), doctorId, pageable)
                : prescriptions.findByOrganizationIdAndDoctorIdAndPatientId(user.organizationId(), doctorId, patientId, pageable)).map(this::view);
    }

    @Transactional(readOnly = true)
    public View get(UUID id) { return view(require(id, "PRESCRIPTIONS:VIEW")); }

    @Transactional
    public View save(Draft input) {
        var user = authorization.requireOwnOrganization();
        authorization.requireDoctorPermission("PRESCRIPTIONS:EDIT", user.organizationId(), input.userId());
        if (input.id() == null || input.patientId() == null || input.userId() == null) bad("Draft ID, patient and doctor are required");
        var doctor = requireDoctor(input.userId(), user.organizationId());
        Long doctorId = doctor.getId();
        var patient = patients.findByIdAndOrganizationId(input.patientId(), user.organizationId()).orElseThrow(this::missing);
        if (input.appointmentId() != null) {
            var appointment = appointments.findById(input.appointmentId()).orElseThrow(this::missing);
            if (!appointment.getOrganization().getId().equals(user.organizationId())
                    || !appointment.getDoctor().getId().equals(doctorId)
                    || appointment.getPatient() == null || !appointment.getPatient().getId().equals(input.patientId())) throw missing();
        } else {
            // Patient-only entry requires explicit organization patient access, not a guessed ID.
            authorization.requireOrganizationPermission("PATIENTS:VIEW", user.organizationId());
            if (!user.hasScope(com.doctor.clinic.DoctorClinic.model.ResourceScope.ORGANIZATION)
                    && !appointments.existsByOrganizationIdAndDoctorIdAndPatientId(user.organizationId(), doctorId, input.patientId())) throw missing();
        }
        PrescriptionRules.validate(input.content(), false);
        String content = json(input.content());
        if (content.length() > 100000) bad("Prescription content is too large");
        var existing = prescriptions.findById(input.id());
        var prescription = existing.orElseGet(Prescription::new);
        if (existing.isPresent()) {
            require(input.id(), "PRESCRIPTIONS:EDIT");
            if (!"DRAFT".equals(prescription.getStatus())) conflict("Finalized prescriptions cannot be edited");
            if (prescription.getVersion() != input.version()) conflict("This draft changed. Reload before saving");
            if (!prescription.getDoctorId().equals(doctorId) || !prescription.getPatientId().equals(input.patientId())
                    || !java.util.Objects.equals(prescription.getAppointmentId(), input.appointmentId())
                    || !java.util.Objects.equals(prescription.getReplacesId(), input.replacesId())) bad("Draft associations cannot be changed");
        } else {
            if (input.replacesId() != null) {
                var original = require(input.replacesId(), "PRESCRIPTIONS:VIEW");
                if (!"FINALIZED".equals(original.getStatus()) || !original.getDoctorId().equals(doctorId)
                        || !original.getPatientId().equals(input.patientId())) bad("Replacement must reference the same doctor and patient");
            }
            prescription.setId(input.id()); prescription.setOrganizationId(user.organizationId());
            prescription.setDoctorId(doctorId); prescription.setPatientId(input.patientId());
            prescription.setAppointmentId(input.appointmentId()); prescription.setReplacesId(input.replacesId());
            prescription.setTemplateSnapshot(json(documentSettings.resolveSnapshot(user.organizationId(), doctorId)));
        }
        prescription.setContent(content); prescription.setUpdatedAt(Instant.now());
        if (prescription.getTemplateSnapshot() == null) {
            prescription.setTemplateSnapshot(json(documentSettings.resolveSnapshot(user.organizationId(), doctorId)));
        }
        var identity = new LinkedHashMap<String, Object>();
        identity.put("patientName", patient.getFullName()); identity.put("age", patient.getAge()); identity.put("gender", patient.getGender());
        identity.put("doctorName", doctor.getFirstName() + " " + (doctor.getLastName() == null ? "" : doctor.getLastName()));
        identity.put("qualification", doctor.getQualification()); identity.put("registrationNumber", doctor.getRegistrationNumber());
        identity.put("organizationName", doctor.getOrganization().getOrganizationName());
        prescription.setIdentitySnapshot(json(identity));
        return view(prescriptions.saveAndFlush(prescription));
    }

    @Transactional
    public View finalizePrescription(UUID id, long version, boolean confirmed) {
        var prescription = require(id, "PRESCRIPTIONS:FINALIZE");
        var user = authorization.requireOwnOrganization();
        var doctor = requireDoctor(user.userId(), user.organizationId());
        if (!doctor.getId().equals(prescription.getDoctorId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the prescribing doctor can finalize");
        if ("FINALIZED".equals(prescription.getStatus())) return view(prescription);
        if (!confirmed) bad("Explicit clinical approval is required");
        if (prescription.getVersion() != version) conflict("The draft changed. Reload and review it");
        PrescriptionRules.validate(read(prescription.getContent(), PrescriptionContent.class), true);
        prescription.setStatus("FINALIZED"); prescription.setFinalizedAt(Instant.now());
        prescription.setFinalizedBy(user.userId()); prescription.setUpdatedAt(Instant.now());
        return view(prescriptions.saveAndFlush(prescription));
    }

    private Prescription require(UUID id, String permission) {
        var user = authorization.requireOwnOrganization();
        var prescription = prescriptions.findByIdAndOrganizationId(id, user.organizationId()).orElseThrow(this::missing);
        Long doctorUserId = doctors.findByIdAndOrganizationId(prescription.getDoctorId(), user.organizationId())
                .map(value -> value.getUser().getId()).orElseThrow(this::missing);
        authorization.requireDoctorPermission(permission, user.organizationId(), doctorUserId);
        return prescription;
    }

    private com.doctor.clinic.DoctorClinic.entity.Doctor requireDoctor(Long userId, Long organizationId) {
        return doctors.findByUserIdAndOrganizationId(userId, organizationId).orElseThrow(this::missing);
    }
    @SuppressWarnings("unchecked")
    private View view(Prescription p) {
        Long userId = doctors.findById(p.getDoctorId()).map(value -> value.getUser().getId()).orElseThrow(this::missing);
        return new View(p.getId(), p.getVersion(), userId, p.getPatientId(), p.getAppointmentId(), p.getReplacesId(),
                p.getStatus(), read(p.getContent(), PrescriptionContent.class), read(p.getIdentitySnapshot(), Map.class),
                p.getTemplateSnapshot() == null ? Map.of() : read(p.getTemplateSnapshot(), Map.class),
                p.getCreatedAt(), p.getUpdatedAt(), p.getFinalizedAt());
    }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize prescription", e); } }
    private <T> T read(String value, Class<T> type) { try { return mapper.readValue(value, type); } catch (JsonProcessingException e) { throw new IllegalStateException("Cannot read prescription", e); } }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription or association not found"); }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }
}
