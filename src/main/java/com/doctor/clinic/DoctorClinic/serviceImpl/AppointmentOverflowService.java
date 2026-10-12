package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.AppointmentOverflowRequest;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.DoctorAvailabilitySettings;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.model.AppointmentOverflowStatus;
import com.doctor.clinic.DoctorClinic.repo.AppointmentOverflowRequestRepo;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorAvailabilitySettingsRepo;
import com.doctor.clinic.DoctorClinic.request.ApproveAppointmentOverflowRequest;
import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.request.CreateAppointmentOverflowRequest;
import com.doctor.clinic.DoctorClinic.response.AppointmentOverflowResponse;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;
import com.doctor.clinic.DoctorClinic.security.CurrentUser;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppointmentOverflowService {
    private final AppointmentOverflowRequestRepo requestRepo;
    private final DoctorRepo doctorRepo;
    private final AppointmentRepo appointmentRepo;
    private final DoctorSlotRepo slotRepo;
    private final DoctorAvailabilitySettingsRepo availabilitySettingsRepo;
    private final WhatsappServiceImpl whatsappService;
    private final AppointmentService appointmentService;

    @Transactional
    public AppointmentOverflowResponse create(CreateAppointmentOverflowRequest input, Long organizationId) {
        Doctor doctor = doctorRepo.findByIdAndOrganizationId(input.getDoctorId(), organizationId)
                .orElseThrow(BusinessException::accessDenied);
        AppointmentOverflowRequest request = new AppointmentOverflowRequest();
        request.setOrganization(doctor.getOrganization());
        request.setDoctor(doctor);
        request.setTargetDate(input.getTargetDate());
        request.setRequestedAdditionalCapacity(input.getRequestedAdditionalCapacity());
        request.setReason(input.getReason());
        request.setDeadlineAt(LocalDateTime.now().plusMinutes(input.getDeadlineMinutes() == null ? 30 : input.getDeadlineMinutes()));
        return toResponse(requestRepo.save(request));
    }

    @Transactional(readOnly = true)
    public List<AppointmentOverflowResponse> list(Long organizationId, Long doctorId) {
        List<AppointmentOverflowRequest> requests = doctorId == null
                ? requestRepo.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : requestRepo.findByOrganizationIdAndDoctorIdOrderByCreatedAtDesc(organizationId, doctorId);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional
    public AppointmentOverflowResponse approve(Long id, Long organizationId, ApproveAppointmentOverflowRequest input,
            CurrentUser user) {
        AppointmentOverflowRequest request = get(id, organizationId);
        if (request.getStatus() != AppointmentOverflowStatus.PENDING || request.getDeadlineAt().isBefore(LocalDateTime.now())) {
            if (request.getStatus() == AppointmentOverflowStatus.PENDING) expire(request);
            throw new BusinessException(409, "OVERFLOW_REQUEST_CLOSED", "This approval request is no longer pending");
        }
        if (input.getApprovedAdditionalCapacity() > request.getRequestedAdditionalCapacity()) {
            throw new BusinessException(400, "CAPACITY_EXCEEDS_REQUEST", "Approved capacity cannot exceed the requested capacity");
        }
        request.setApprovedAdditionalCapacity(input.getApprovedAdditionalCapacity());
        request.setStatus(input.getApprovedAdditionalCapacity().equals(request.getRequestedAdditionalCapacity())
                ? AppointmentOverflowStatus.APPROVED : AppointmentOverflowStatus.PARTIALLY_APPROVED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(user.name() == null ? user.email() : user.name());
        AppointmentOverflowRequest saved = requestRepo.save(request);
        processApprovedCarryForward(saved);
        return toResponse(saved);
    }

    @Transactional
    public AppointmentOverflowResponse reject(Long id, Long organizationId, CurrentUser user) {
        AppointmentOverflowRequest request = get(id, organizationId);
        if (request.getStatus() != AppointmentOverflowStatus.PENDING) {
            throw new BusinessException(409, "OVERFLOW_REQUEST_CLOSED", "This approval request is no longer pending");
        }
        request.setStatus(AppointmentOverflowStatus.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy(user.name() == null ? user.email() : user.name());
        return toResponse(requestRepo.save(request));
    }

    @Transactional
    public int expirePending() {
        int count = 0;
        for (AppointmentOverflowRequest request : requestRepo.findByStatusAndDeadlineAtBefore(
                AppointmentOverflowStatus.PENDING, LocalDateTime.now())) {
            expire(request);
            count++;
        }
        return count;
    }

    /**
     * At the end of the current doctor's day, carry forward only appointments
     * that were booked but never seen. The request is for tomorrow's capacity.
     */
    @Transactional
    public int createAutomaticNextDayRequests() {
        LocalDate today = LocalDate.now();
        LocalDate targetDate = today.plusDays(1);
        LocalTime now = LocalTime.now();
        int created = 0;
        for (Doctor doctor : doctorRepo.findByStatus("ACTIVE")) {
            doctor = doctorRepo.findByIdForUpdate(doctor.getId()).orElse(null);
            if (doctor == null) continue;
            if (Boolean.FALSE.equals(doctor.getOrganization().getAppointmentAutomationEnabled())) continue;
            List<Appointment> todayAppointments = appointmentRepo
                    .findByDoctorIdAndAppointmentDate(doctor.getId(), today).stream()
                    .filter(this::isStillUnseen).sorted(Comparator.comparing(Appointment::getAppointmentTime))
                    .toList();
            if (todayAppointments.isEmpty()) continue;

            // Do not ask early while the doctor still has a later appointment today.
            LocalTime lastAppointmentEnd = appointmentRepo.findByDoctorIdAndAppointmentDate(doctor.getId(), today)
                    .stream().map(this::appointmentEnd).max(LocalTime::compareTo).orElse(now);
            if (now.isBefore(lastAppointmentEnd)) continue;

            boolean alreadyRequested = requestRepo.findByDoctorIdAndTargetDateOrderByCreatedAtDesc(doctor.getId(), targetDate)
                    .stream().anyMatch(request -> request.getStatus() == AppointmentOverflowStatus.PENDING
                            || request.getStatus() == AppointmentOverflowStatus.APPROVED
                            || request.getStatus() == AppointmentOverflowStatus.PARTIALLY_APPROVED);
            if (alreadyRequested) continue;

            AppointmentOverflowRequest request = new AppointmentOverflowRequest();
            request.setOrganization(doctor.getOrganization());
            request.setDoctor(doctor);
            request.setTargetDate(targetDate);
            request.setRequestedAdditionalCapacity(todayAppointments.size());
            request.setApprovedAdditionalCapacity(0);
            request.setReason("Automatically created for " + todayAppointments.size()
                    + " appointment(s) booked today but not seen before the clinic day ended.");
            request.setDeadlineAt(LocalDateTime.now().plusMinutes(30));
            AppointmentOverflowRequest saved = requestRepo.save(request);
            sendDoctorDecisionRequest(saved);
            created++;
        }
        return created;
    }

    @Transactional
    public String handleWhatsAppDecision(Long doctorId, String selectionId, String messageText) {
        if (selectionId == null || !selectionId.startsWith("overflow_")) {
            String normalized = messageText == null ? "" : messageText.trim().toLowerCase();
            if (!normalized.matches("(approve|approved|accept|yes|reject|rejected|decline|no)")) return null;
            AppointmentOverflowRequest latest = requestRepo.findByDoctorIdAndTargetDateOrderByCreatedAtDesc(
                    doctorId, LocalDate.now().plusDays(1)).stream()
                    .filter(request -> request.getStatus() == AppointmentOverflowStatus.PENDING)
                    .findFirst().orElse(null);
            if (latest == null) return "There is no pending extra-capacity request.";
            selectionId = "overflow_" + latest.getId() + "_"
                    + (normalized.matches("(approve|approved|accept|yes)") ? "approve" : "reject");
        }
        String[] parts = selectionId.split("_");
        if (parts.length != 3) return "That approval request is not valid.";
        AppointmentOverflowRequest request;
        try {
            request = requestRepo.findByIdAndOrganizationIdForUpdate(Long.parseLong(parts[1]),
                    doctorRepo.findById(doctorId).map(d -> d.getOrganization().getId()).orElse(-1L)).orElse(null);
        } catch (NumberFormatException ex) {
            return "That approval request is not valid.";
        }
        if (request == null || !request.getDoctor().getId().equals(doctorId)) return "That approval request is not available.";
        if (request.getStatus() != AppointmentOverflowStatus.PENDING || request.getDeadlineAt().isBefore(LocalDateTime.now())) {
            if (request.getStatus() == AppointmentOverflowStatus.PENDING) expire(request);
            return "This approval request has expired or was already decided.";
        }
        boolean approved = "approve".equalsIgnoreCase(parts[2]);
        request.setApprovedAdditionalCapacity(approved ? request.getRequestedAdditionalCapacity() : 0);
        request.setStatus(approved ? AppointmentOverflowStatus.APPROVED : AppointmentOverflowStatus.REJECTED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy("WHATSAPP_DOCTOR");
        requestRepo.save(request);
        if (approved) processApprovedCarryForward(request);
        return approved ? "Extra capacity approved for " + request.getTargetDate() + "." : "Extra capacity rejected for " + request.getTargetDate() + ".";
    }

    private boolean isStillUnseen(Appointment appointment) {
        return "SCHEDULED".equals(appointment.getAppointmentStatus())
                || "CONFIRMED".equals(appointment.getAppointmentStatus());
    }

    private LocalTime appointmentEnd(Appointment appointment) {
        if (appointment.getEndTime() != null) return appointment.getEndTime();
        int duration = appointment.getSlotDurationMinutes() == null ? 30 : appointment.getSlotDurationMinutes();
        return appointment.getAppointmentTime().plusMinutes(duration);
    }

    /** Books approved carry-forward patients through the normal locked booking path and notifies them. */
    private void processApprovedCarryForward(AppointmentOverflowRequest request) {
        if (request.getApprovedAdditionalCapacity() == null || request.getApprovedAdditionalCapacity() < 1) return;
        LocalDate today = LocalDate.now();
        List<Appointment> unseen = appointmentRepo.findByDoctorIdAndAppointmentDate(request.getDoctor().getId(), today)
                .stream().filter(this::isStillUnseen).sorted(Comparator.comparing(Appointment::getAppointmentTime))
                .limit(request.getApprovedAdditionalCapacity()).toList();
        List<DoctorSlot> slots = slotRepo.findByDoctorIdAndSlotDateAndIsAvailableTrue(request.getDoctor().getId(), request.getTargetDate())
                .stream().filter(DoctorSlot::hasAvailability)
                .sorted(Comparator.comparing(DoctorSlot::getStartTime)).toList();
        int count = Math.min(unseen.size(), slots.size());
        for (int i = 0; i < count; i++) {
            Appointment original = unseen.get(i);
            DoctorSlot slot = slots.get(i);
            try {
                BookAppointmentRequest booking = new BookAppointmentRequest();
                booking.setDoctorId(request.getDoctor().getId());
                booking.setPatientName(original.getPatientName());
                booking.setPatientPhone(original.getPatientPhone());
                booking.setPatientEmail(original.getPatientEmail());
                booking.setPatientAge(original.getPatientAge());
                booking.setPatientGender(original.getPatientGender());
                booking.setAppointmentDate(request.getTargetDate());
                booking.setAppointmentTime(slot.getStartTime());
                booking.setAppointmentType(original.getAppointmentType());
                booking.setReasonForVisit(original.getReasonForVisit());
                booking.setPaymentMethod("CASH");
                booking.setBookedVia("SYSTEM");
                var moved = appointmentService.bookAppointment(booking);
                original.setAppointmentStatus("RESCHEDULED");
                original.setCancellationReason("Automatically moved to " + moved.getAppointmentDate()
                        + " after doctor approved carry-forward capacity.");
                original.setCancelledBy("SYSTEM");
                appointmentRepo.save(original);
                if (original.getPatientPhone() != null && !original.getPatientPhone().isBlank()) {
                    whatsappService.sendMessage(request.getDoctor(), original.getPatientPhone(),
                            "Your appointment with Dr. " + request.getDoctor().getFullName()
                                    + " has been moved to " + moved.getAppointmentDate() + " at "
                                    + moved.getAppointmentTime() + ".");
                }
            } catch (RuntimeException ex) {
                // A slot can be taken concurrently; leave that patient unresolved for staff review.
            }
        }
    }

    private void sendDoctorDecisionRequest(AppointmentOverflowRequest request) {
        Doctor doctor = request.getDoctor();
        String phone = doctor.getPhoneNumber();
        if (phone == null || phone.isBlank()) return;
        String destination = phone.replaceAll("\\D", "");
        if (destination.length() == 10) destination = "91" + destination;
        String body = "Extra capacity approval needed for " + request.getTargetDate() + ": "
                + request.getRequestedAdditionalCapacity() + " additional patient(s). Please accept or reject.";
        Map<String, Object> interactive = Map.of(
                "type", "button", "body", Map.of("text", body), "action", Map.of("buttons", List.of(
                        Map.of("type", "reply", "reply", Map.of("id", "overflow_" + request.getId() + "_approve", "title", "Approve")),
                        Map.of("type", "reply", "reply", Map.of("id", "overflow_" + request.getId() + "_reject", "title", "Reject")))));
        whatsappService.sendInteractiveWithResult(doctor, destination, interactive);
    }

    private void expire(AppointmentOverflowRequest request) {
        request.setStatus(AppointmentOverflowStatus.EXPIRED);
        request.setDecidedAt(LocalDateTime.now());
        request.setDecidedBy("SYSTEM");
        requestRepo.save(request);
    }

    private AppointmentOverflowRequest get(Long id, Long organizationId) {
        return requestRepo.findByIdAndOrganizationIdForUpdate(id, organizationId)
                .orElseThrow(BusinessException::accessDenied);
    }

    private AppointmentOverflowResponse toResponse(AppointmentOverflowRequest request) {
        Long doctorId = request.getDoctor().getId();
        LocalDate date = request.getTargetDate();
        int booked = (int) appointmentRepo.countActiveByDoctorIdAndAppointmentDate(doctorId, date);
        int available = slotRepo.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctorId, date).stream()
                .mapToInt(slot -> Math.max(0, slot.getMaxAppointments() - slot.getBookedCount())).sum();
        int unresolved = (int) appointmentRepo.findByDoctorIdAndAppointmentDateAndAppointmentStatusNotIn(
                doctorId, date, List.of("COMPLETED", "CANCELLED", "NO_SHOW")).size();
        return AppointmentOverflowResponse.builder().id(request.getId()).doctorId(doctorId)
                .doctorName(request.getDoctor().getFullName()).targetDate(date)
                .requestedAdditionalCapacity(request.getRequestedAdditionalCapacity())
                .approvedAdditionalCapacity(request.getApprovedAdditionalCapacity()).currentBooked(booked)
                .availableSlots(available).unresolvedPatients(unresolved).status(request.getStatus())
                .reason(request.getReason()).deadlineAt(request.getDeadlineAt()).decidedAt(request.getDecidedAt())
                .decidedBy(request.getDecidedBy()).createdAt(request.getCreatedAt()).build();
    }
}
