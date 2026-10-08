package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.BookingRequest;
import com.doctor.clinic.DoctorClinic.entity.BookingRequestEvent;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppConversation;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppMessage;
import com.doctor.clinic.DoctorClinic.model.BookingRequestStatus;
import com.doctor.clinic.DoctorClinic.repo.BookingRequestEventRepo;
import com.doctor.clinic.DoctorClinic.repo.BookingRequestRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppConversationRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppMessageRepo;
import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.response.AvailableSlotResponse;
import com.doctor.clinic.DoctorClinic.response.BookingRequestDetailResponse;
import com.doctor.clinic.DoctorClinic.response.BookingRequestEventResponse;
import com.doctor.clinic.DoctorClinic.response.BookingRequestResponse;
import com.doctor.clinic.DoctorClinic.response.ConversationMessageResponse;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class BookingRequestService {

    private final BookingRequestRepo bookingRequestRepo;
    private final BookingRequestEventRepo eventRepo;
    private final DoctorSlotRepo doctorSlotRepo;
    private final WhatsAppConversationRepo conversationRepo;
    private final WhatsAppMessageRepo messageRepo;
    private final AppointmentService appointmentService;
    private final WhatsAppConversationServiceImpl conversationService;

    /** Records a pending request, reusing an existing open one for the same patient and doctor. */
    @Transactional
    public BookingRequest createPending(Doctor doctor, Patient patient, String appointmentType, String source,
            String reason, LocalDate requestedDate, LocalTime requestedTime) {
        BookingRequest request = bookingRequestRepo
                .findFirstByPatientIdAndDoctorIdAndStatus(patient.getId(), doctor.getId(),
                        BookingRequestStatus.PENDING)
                .orElseGet(() -> {
                    BookingRequest r = new BookingRequest();
                    r.setOrganization(doctor.getOrganization());
                    r.setDoctor(doctor);
                    r.setPatient(patient);
                    r.setPatientName(patient.getFullName());
                    r.setPatientPhone(patient.getNormalizedPhone());
                    r.setAppointmentType(appointmentType);
                    r.setSource(source);
                    return r;
                });
        request.setReason(reason);
        request.setRequestedDate(requestedDate);
        request.setRequestedTime(requestedTime);
        BookingRequest saved = bookingRequestRepo.save(request);
        addEvent(saved, "CREATED", "Patient requested an appointment via " + source + ". Reason: " + reason);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<BookingRequestResponse> list(Long organizationId, BookingRequestStatus status) {
        List<BookingRequest> requests = status == null
                ? bookingRequestRepo.findByOrganizationIdOrderByCreatedAtDesc(organizationId)
                : bookingRequestRepo.findByOrganizationIdAndStatusOrderByCreatedAtDesc(organizationId, status);
        return requests.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public BookingRequestDetailResponse getDetail(Long id, Long organizationId) {
        BookingRequest request = bookingRequestRepo.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(BusinessException::accessDenied);

        List<BookingRequestEventResponse> events = eventRepo.findByBookingRequestIdOrderByCreatedAtAsc(id).stream()
                .map(e -> BookingRequestEventResponse.builder().eventType(e.getEventType())
                        .description(e.getDescription()).createdAt(e.getCreatedAt()).build())
                .toList();

        List<ConversationMessageResponse> conversation = request.getPatient() == null ? List.of()
                : conversationRepo
                        .findByOrganizationIdAndPatientIdAndPhoneNumber(organizationId, request.getPatient().getId(),
                                request.getPatientPhone())
                        .map(WhatsAppConversation::getId)
                        .map(convId -> messageRepo.findByConversationIdOrderByCreatedAtDesc(convId, PageRequest.of(0, 20)))
                        .map(messages -> messages.stream()
                                .sorted(Comparator.comparing(WhatsAppMessage::getCreatedAt))
                                .map(m -> ConversationMessageResponse.builder().direction(m.getDirection().name())
                                        .content(m.getContent()).createdAt(m.getCreatedAt()).build())
                                .toList())
                        .orElse(List.of());

        return BookingRequestDetailResponse.builder().request(toResponse(request)).events(events)
                .conversation(conversation).build();
    }

    /** Available slots for the request's doctor over the next 14 days, date-ascending, for the Offer Slot dialog. */
    @Transactional(readOnly = true)
    public List<AvailableSlotResponse> availableSlots(Long id, Long organizationId) {
        BookingRequest request = bookingRequestRepo.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(BusinessException::accessDenied);
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        List<DoctorSlot> slots = doctorSlotRepo.findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(
                request.getDoctor().getId(), today, today.plusDays(14));
        return slots.stream()
                .filter(DoctorSlot::hasAvailability)
                .filter(s -> s.getSlotDate().isAfter(today) || (s.getSlotDate().equals(today) && s.getStartTime().isAfter(now)))
                .sorted(Comparator.comparing(DoctorSlot::getSlotDate).thenComparing(DoctorSlot::getStartTime))
                .map(s -> AvailableSlotResponse.builder().slotId(s.getId()).date(s.getSlotDate())
                        .startTime(s.getStartTime()).endTime(s.getEndTime()).build())
                .toList();
    }

    /** Staff picks one or more alternative slots; they're sent to the patient as a WhatsApp list to choose from. */
    @Transactional
    public BookingRequestResponse offerSlots(Long id, Long organizationId, List<Long> slotIds) {
        BookingRequest request = bookingRequestRepo.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(BusinessException::accessDenied);
        if (request.getPatient() == null) {
            throw new IllegalStateException("This request has no linked patient to message");
        }

        List<DoctorSlot> slots = doctorSlotRepo.findAllById(slotIds).stream()
                .filter(s -> s.getDoctor().getId().equals(request.getDoctor().getId()))
                .sorted(Comparator.comparing(DoctorSlot::getSlotDate).thenComparing(DoctorSlot::getStartTime))
                .toList();
        if (slots.isEmpty()) {
            throw new IllegalArgumentException("None of the selected slots belong to this doctor");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (DoctorSlot slot : slots) {
            String title = slot.getSlotDate().format(DateTimeFormatter.ofPattern("dd MMM")) + ", "
                    + slot.getStartTime().format(DateTimeFormatter.ofPattern("h:mm a"));
            rows.add(Map.of("id", "offer_" + request.getId() + "_" + slot.getId(),
                    "title", title.length() > 24 ? title.substring(0, 24) : title,
                    "description", "Tap to book this time"));
        }
        String body = "Dr. " + request.getDoctor().getFullName()
                + " now has these appointment times available. Please choose one:";
        Map<String, Object> interactive = Map.of("type", "list", "body", Map.of("text", body), "action",
                Map.of("button", "Choose time", "sections", List.of(Map.of("title", "Available times", "rows", rows))));

        conversationService.sendAutomatedMessage(organizationId, request.getPatient(), request.getDoctor(),
                request.getPatientPhone(), body, interactive);

        request.setOfferedSlotIds(slots.stream().map(s -> String.valueOf(s.getId())).collect(Collectors.joining(",")));
        request.setOfferedAt(java.time.LocalDateTime.now());
        request.setStatus(BookingRequestStatus.CONTACTED);
        BookingRequest saved = bookingRequestRepo.save(request);
        addEvent(saved, "OFFER_SENT", "Offered " + slots.size() + " alternative slot(s) to the patient on WhatsApp.");
        return toResponse(saved);
    }

    /** Called from the WhatsApp inbound pipeline when a patient taps one of the offered slots. */
    @Transactional
    public String confirmOffer(Long requestId, Long slotId) {
        BookingRequest request = bookingRequestRepo.findById(requestId).orElse(null);
        if (request == null || request.getStatus() == BookingRequestStatus.BOOKED) {
            return "Sorry, that request is no longer valid.";
        }
        String offered = request.getOfferedSlotIds();
        boolean wasOffered = offered != null && List.of(offered.split(",")).contains(String.valueOf(slotId));
        if (!wasOffered) {
            return "That option is no longer available. Please ask the clinic for the latest options.";
        }

        DoctorSlot slot = doctorSlotRepo.findById(slotId).orElse(null);
        if (slot == null || !slot.hasAvailability()) {
            addEvent(request, "STATUS_CHANGE", "Selected slot was no longer available when the patient confirmed.");
            return "That slot was just taken. Please ask the clinic to offer you another time.";
        }

        BookAppointmentRequest bookingRequest = new BookAppointmentRequest();
        bookingRequest.setDoctorId(request.getDoctor().getId());
        bookingRequest.setPatientName(request.getPatientName());
        bookingRequest.setPatientPhone(request.getPatientPhone());
        bookingRequest.setAppointmentDate(slot.getSlotDate());
        bookingRequest.setAppointmentTime(slot.getStartTime());
        bookingRequest.setAppointmentType(request.getAppointmentType());
        bookingRequest.setReasonForVisit(request.getAppointmentType());
        bookingRequest.setPaymentMethod("CASH");
        bookingRequest.setBookedVia("WHATSAPP");

        try {
            var booked = appointmentService.bookAppointment(bookingRequest);
            request.setStatus(BookingRequestStatus.BOOKED);
            bookingRequestRepo.save(request);
            addEvent(request, "BOOKED", "Patient confirmed " + slot.getSlotDate() + " at " + slot.getStartTime()
                    + "; appointment #" + booked.getAppointmentId() + " created.");
            return "You're booked with Dr. " + request.getDoctor().getFullName() + " on " + booked.getAppointmentDate()
                    + " at " + booked.getAppointmentTime() + ".";
        } catch (RuntimeException e) {
            addEvent(request, "STATUS_CHANGE", "Booking failed when patient confirmed: " + e.getMessage());
            return "Sorry, that slot was just booked by someone else. Please ask the clinic for another time.";
        }
    }

    @Transactional
    public BookingRequestResponse updateStatus(Long id, Long organizationId, BookingRequestStatus status,
            String note) {
        BookingRequest request = bookingRequestRepo.findByIdAndOrganizationId(id, organizationId)
                .orElseThrow(BusinessException::accessDenied);
        BookingRequestStatus previous = request.getStatus();
        request.setStatus(status);
        if (note != null) {
            request.setNote(note.length() > 500 ? note.substring(0, 500) : note);
        }
        BookingRequest saved = bookingRequestRepo.save(request);
        if (previous != status) {
            addEvent(saved, "STATUS_CHANGE", "Status changed from " + previous + " to " + status
                    + (note == null || note.isBlank() ? "" : ": " + note));
        }
        return toResponse(saved);
    }

    private void addEvent(BookingRequest request, String type, String description) {
        BookingRequestEvent event = new BookingRequestEvent();
        event.setBookingRequest(request);
        event.setEventType(type);
        event.setDescription(description);
        eventRepo.save(event);
    }

    private BookingRequestResponse toResponse(BookingRequest r) {
        return BookingRequestResponse.builder()
                .id(r.getId())
                .doctorId(r.getDoctor().getId())
                .doctorName(r.getDoctor().getFullName())
                .patientName(r.getPatientName())
                .patientPhone(r.getPatientPhone())
                .appointmentType(r.getAppointmentType())
                .requestedDate(r.getRequestedDate())
                .requestedTime(r.getRequestedTime())
                .reason(r.getReason())
                .source(r.getSource())
                .status(r.getStatus())
                .note(r.getNote())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
