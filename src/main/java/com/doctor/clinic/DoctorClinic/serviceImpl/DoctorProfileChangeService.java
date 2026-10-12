package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.DoctorProfileChangeRequest;
import com.doctor.clinic.DoctorClinic.model.DoctorProfileChangeStatus;
import com.doctor.clinic.DoctorClinic.repo.DoctorProfileChangeRequestRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.request.DoctorProfileChangeRequestPayload;
import com.doctor.clinic.DoctorClinic.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorProfileChangeService {
    private static final Set<String> ALLOWED_FIELDS = Set.of("qualification", "experienceYears", "specialization",
            "registrationNumber", "bio", "consultationFee", "defaultSlotDurationMinutes", "consultationHours");
    private final DoctorProfileChangeRequestRepo requestRepo;
    private final DoctorRepo doctorRepo;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> submit(Long doctorId, Long organizationId, CurrentUser user,
            DoctorProfileChangeRequestPayload payload) {
        Doctor doctor = doctorRepo.findByIdAndOrganizationId(doctorId, organizationId)
                .orElseThrow(BusinessException::accessDenied);
        if (!ALLOWED_FIELDS.containsAll(payload.getProposedChanges().keySet())) {
            throw new BusinessException(400, "INVALID_PROFILE_FIELDS", "One or more requested fields cannot be changed here");
        }
        if (requestRepo.existsByOrganizationIdAndDoctorIdAndStatus(organizationId, doctorId, DoctorProfileChangeStatus.PENDING)) {
            throw new BusinessException(409, "PENDING_PROFILE_REQUEST_EXISTS", "A profile change request is already waiting for review");
        }
        try {
            DoctorProfileChangeRequest request = new DoctorProfileChangeRequest();
            request.setOrganization(doctor.getOrganization()); request.setDoctor(doctor);
            request.setRequesterUserId(user.userId()); request.setRequesterName(user.name()); request.setRequesterEmail(user.email());
            request.setReason(payload.getReason().trim());
            request.setProposedChanges(objectMapper.writeValueAsString(payload.getProposedChanges()));
            return toMap(requestRepo.save(request));
        } catch (Exception e) { throw new BusinessException(400, "INVALID_PROFILE_REQUEST", "Profile changes could not be saved"); }
    }

    @Transactional
    public void updateDirectly(Long doctorId, Long organizationId, DoctorProfileChangeRequestPayload payload) {
        Doctor doctor = doctorRepo.findByIdAndOrganizationId(doctorId, organizationId)
                .orElseThrow(BusinessException::accessDenied);
        if (!ALLOWED_FIELDS.containsAll(payload.getProposedChanges().keySet())) {
            throw new BusinessException(400, "INVALID_PROFILE_FIELDS", "One or more requested fields cannot be changed here");
        }
        try {
            apply(doctor, objectMapper.writeValueAsString(payload.getProposedChanges()));
        } catch (Exception e) {
            throw new BusinessException(400, "INVALID_PROFILE_REQUEST", "Profile changes could not be saved");
        }
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long organizationId, Long doctorId, DoctorProfileChangeStatus status) {
        List<DoctorProfileChangeRequest> requests = doctorId != null && status != null
                ? requestRepo.findByOrganizationIdAndDoctorIdAndStatusOrderByCreatedAtDesc(organizationId, doctorId, status)
                : status != null
                    ? requestRepo.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status)
                    : doctorId == null
                        ? requestRepo.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                        : requestRepo.findByOrganizationIdAndDoctorIdOrderByCreatedAtDesc(organizationId, doctorId);
        return requests.stream().map(this::toMap).toList();
    }

    @Transactional
    public Map<String, Object> review(Long id, Long organizationId, CurrentUser reviewer, boolean approve, String note) {
        DoctorProfileChangeRequest request = requestRepo.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(BusinessException::accessDenied);
        if (request.getStatus() != DoctorProfileChangeStatus.PENDING)
            throw new BusinessException(409, "REQUEST_CLOSED", "This profile request is already decided");
        if (reviewer.userId() != null && reviewer.userId().equals(request.getRequesterUserId()))
            throw new BusinessException(403, "SELF_APPROVAL_NOT_ALLOWED", "You cannot approve your own request");
        if (approve) apply(request.getDoctor(), request.getProposedChanges());
        request.setStatus(approve ? DoctorProfileChangeStatus.APPROVED : DoctorProfileChangeStatus.REJECTED);
        request.setReviewNote(note); request.setReviewedByUserId(reviewer.userId()); request.setReviewedByName(reviewer.name());
        request.setReviewedAt(LocalDateTime.now());
        return toMap(requestRepo.save(request));
    }

    private void apply(Doctor doctor, String json) {
        try {
            Map<String, Object> values = objectMapper.readValue(json, new TypeReference<>() {});
            if (values.containsKey("qualification")) doctor.setQualification(String.valueOf(values.get("qualification")));
            if (values.containsKey("experienceYears")) doctor.setExperienceYears(asInteger(values.get("experienceYears"), "experienceYears"));
            if (values.containsKey("specialization")) doctor.setSpecialization(String.valueOf(values.get("specialization")));
            if (values.containsKey("registrationNumber")) doctor.setRegistrationNumber(String.valueOf(values.get("registrationNumber")));
            if (values.containsKey("bio")) doctor.setBio(String.valueOf(values.get("bio")));
            if (values.containsKey("consultationFee")) doctor.setConsultationFee(asDecimal(values.get("consultationFee"), "consultationFee"));
            if (values.containsKey("defaultSlotDurationMinutes")) doctor.setDefaultSlotDurationMinutes(asInteger(values.get("defaultSlotDurationMinutes"), "defaultSlotDurationMinutes"));
            if (values.containsKey("consultationHours")) doctor.setConsultationHours(String.valueOf(values.get("consultationHours")));
            doctorRepo.saveAndFlush(doctor);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(409, "PROFILE_CONFLICT", "Registration number or another profile value is already in use");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(400, "INVALID_PROFILE_VALUES", "Proposed profile values are invalid");
        }
    }

    private Integer asInteger(Object value, String field) {
        try {
            if (value instanceof Number number) return new java.math.BigDecimal(String.valueOf(number)).intValueExact();
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(400, "INVALID_PROFILE_VALUES", field + " must be a whole number");
        }
    }

    private java.math.BigDecimal asDecimal(Object value, String field) {
        try {
            return new java.math.BigDecimal(String.valueOf(value).trim());
        } catch (Exception e) {
            throw new BusinessException(400, "INVALID_PROFILE_VALUES", field + " must be a valid amount");
        }
    }

    private Map<String, Object> toMap(DoctorProfileChangeRequest r) {
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("id", r.getId());
            result.put("doctorId", r.getDoctor().getId());
            result.put("doctorName", r.getDoctor().getFullName());
            result.put("requesterName", r.getRequesterName());
            result.put("requesterEmail", r.getRequesterEmail());
            result.put("reason", r.getReason());
            result.put("proposedChanges", objectMapper.readValue(r.getProposedChanges(), new TypeReference<Map<String, Object>>() {}));
            result.put("status", r.getStatus());
            result.put("reviewNote", r.getReviewNote() == null ? "" : r.getReviewNote());
            result.put("createdAt", r.getCreatedAt());
            result.put("reviewedByName", r.getReviewedByName() == null ? "" : r.getReviewedByName());
            result.put("reviewedAt", r.getReviewedAt());
            return result;
        } catch (Exception e) { throw new IllegalStateException("Invalid stored profile request", e); }
    }
}
