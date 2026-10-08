package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import com.doctor.clinic.DoctorClinic.entity.*;
import com.doctor.clinic.DoctorClinic.repo.*;
import com.doctor.clinic.DoctorClinic.request.BookAppointmentRequest;
import com.doctor.clinic.DoctorClinic.service.AppointmentService;

@Service
public class AppointmentBookingChatService {
    public record Reply(String text, Map<String,Object> interactive) {}
    private final PatientBookingSessionRepo sessionRepo;
    private final DoctorSlotRepo slotRepo;
    private final AppointmentService appointmentService;
    private final BookingRequestService bookingRequestService;
    public AppointmentBookingChatService(PatientBookingSessionRepo sessionRepo, DoctorSlotRepo slotRepo, AppointmentService appointmentService,
                                         BookingRequestService bookingRequestService) {
        this.sessionRepo=sessionRepo; this.slotRepo=slotRepo; this.appointmentService=appointmentService; this.bookingRequestService=bookingRequestService;
    }

    private static boolean isBookable(DoctorSlot slot, LocalDate today, LocalTime now) {
        if(!slot.hasAvailability()) return false;
        return slot.getSlotDate().isAfter(today) || (slot.getSlotDate().equals(today) && slot.getStartTime().isAfter(now));
    }

    // Intentionally not transactional: a failed booking inside an outer transaction would mark it
    // rollback-only and discard the session update that sends the patient back to choose another time.
    public Optional<Reply> handle(Doctor doctor, Patient patient, String message, String selectionId) {
        Optional<PatientBookingSession> found=sessionRepo.findByPatientIdAndDoctorId(patient.getId(), doctor.getId());
        String normalized=message==null ? "" : message.trim().toLowerCase(Locale.ROOT);
        if (normalized.matches(".*\\b(cancel|stop booking)\\b.*")) {
            if(found.isPresent()) sessionRepo.delete(found.get());
            return Optional.of(new Reply("Okay, I cancelled the appointment booking. You can start again any time by saying ‘book appointment’.",null));
        }
        PatientBookingSession s;
        if(found.isEmpty()) {
            if (!(normalized.contains("appointment") && (normalized.contains("book") || normalized.contains("schedule") || normalized.contains("want")))) return Optional.empty();
            s=new PatientBookingSession(); s.setPatient(patient); s.setDoctor(doctor); s.setStep("TYPE"); sessionRepo.save(s);
            return Optional.of(typeChoices(doctor));
        }
        s=found.get();
        if(selectionId==null || selectionId.isBlank()) selectionId=message==null?"":message.trim();
        switch(s.getStep()) {
            case "TYPE" -> {
                String id=selectionId.toUpperCase(Locale.ROOT);
                s.setAppointmentType(id.contains("FOLLOW") || normalized.contains("follow") ? "FOLLOW_UP" : "CONSULTATION");
                s.setStep("DATE"); sessionRepo.save(s); return Optional.of(dateChoices(doctor,patient,s));
            }
            case "DATE" -> {
                try { s.setAppointmentDate(LocalDate.parse(selectionId.replace("BOOK_DATE_",""))); }
                catch(Exception e) { return Optional.of(dateChoices(doctor,patient,s)); }
                s.setStep("TIME"); sessionRepo.save(s); return Optional.of(timeChoices(doctor,patient,s));
            }
            case "TIME" -> {
                try { s.setAppointmentTime(LocalTime.parse(selectionId.replace("BOOK_TIME_",""))); }
                catch(Exception e) { return Optional.of(timeChoices(doctor,patient,s)); }
                s.setStep("CONFIRM"); sessionRepo.save(s); return Optional.of(confirm(doctor,s));
            }
            case "CONFIRM" -> {
                String id=selectionId.toLowerCase(Locale.ROOT);
                if(id.contains("change") || id.contains("back")) { s.setStep("DATE"); sessionRepo.save(s); return Optional.of(dateChoices(doctor,patient,s)); }
                if(!(id.contains("confirm") || normalized.matches("(yes|y|confirm|book it|ok|okay)"))) return Optional.of(confirm(doctor,s));
                BookAppointmentRequest request=new BookAppointmentRequest();
                request.setDoctorId(doctor.getId()); request.setPatientName(patient.getFullName()); request.setPatientPhone(patient.getNormalizedPhone());
                request.setPatientEmail(patient.getEmail()); request.setPatientAge(patient.getAge()); request.setPatientGender(patient.getGender());
                request.setAppointmentDate(s.getAppointmentDate()); request.setAppointmentTime(s.getAppointmentTime()); request.setAppointmentType(s.getAppointmentType());
                request.setPaymentMethod("CASH"); request.setReasonForVisit(s.getAppointmentType()); request.setBookedVia("WHATSAPP");
                try {
                    var booked=appointmentService.bookAppointment(request); sessionRepo.delete(s);
                    return Optional.of(new Reply("Appointment booked for " + booked.getAppointmentDate() + " at " + booked.getAppointmentTime()
                            + " with " + doctor.getFullName() + ". Status: " + booked.getAppointmentStatus()
                            + "; payment: " + booked.getPaymentStatus() + ". Please contact the clinic for payment instructions.",null));
                } catch(RuntimeException e) {
                    s.setStep("TIME"); sessionRepo.save(s);
                    return Optional.of(new Reply("That slot is no longer available. Please choose another time.",timeChoices(doctor,patient,s).interactive()));
                }
            }
            default -> { sessionRepo.delete(s); return Optional.of(typeChoices(doctor)); }
        }
    }

    private Reply typeChoices(Doctor doctor) {
        String specialty=doctor.getSpecialization()==null || doctor.getSpecialization().isBlank()?"Consultation":doctor.getSpecialization().trim()+" consultation";
        return buttons("What type of visit would you like with Dr. "+doctor.getFullName()+"?", List.of(btn("BOOK_TYPE_CONSULT",specialty),btn("BOOK_TYPE_FOLLOW_UP","Follow-up")));
    }
    private Reply dateChoices(Doctor doctor, Patient patient, PatientBookingSession s) {
        LocalDate today=LocalDate.now(); LocalTime now=LocalTime.now();
        List<DoctorSlot> slots=slotRepo.findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(doctor.getId(),today,today.plusDays(30));
        TreeSet<LocalDate> dates=new TreeSet<>(); slots.stream().filter(sl -> isBookable(sl,today,now)).map(DoctorSlot::getSlotDate).forEach(dates::add);
        List<Map<String,Object>> rows=dates.stream().limit(10).map(d -> row("BOOK_DATE_"+d,d.format(DateTimeFormatter.ofPattern("EEE, dd MMM")),"Available appointment times")).toList();
        if(rows.isEmpty()) {
            bookingRequestService.createPending(doctor,patient,s.getAppointmentType(),"WHATSAPP","No slots available",s.getAppointmentDate(),s.getAppointmentTime());
            sessionRepo.delete(s);
            return new Reply("Dr. "+doctor.getFullName()+" has no open appointment times in the next 30 days. I've passed your request to the clinic and they will contact you as soon as a time opens up. You can say ‘book appointment’ to try again later.",null);
        }
        return list("Please choose an available date.","Choose date","Available dates",rows);
    }
    private Reply timeChoices(Doctor doctor, Patient patient, PatientBookingSession s) {
        LocalDate date=s.getAppointmentDate(); LocalDate today=LocalDate.now(); LocalTime now=LocalTime.now();
        List<Map<String,Object>> rows=slotRepo.findByDoctorIdAndSlotDateAndIsAvailableTrue(doctor.getId(),date).stream().filter(sl -> isBookable(sl,today,now))
                .sorted(Comparator.comparing(DoctorSlot::getStartTime)).limit(10)
                .map(slot -> row("BOOK_TIME_"+slot.getStartTime(),slot.getStartTime().format(DateTimeFormatter.ofPattern("h:mm a")),"Available" )).toList();
        if(rows.isEmpty()) { s.setStep("DATE"); sessionRepo.save(s); Reply dates=dateChoices(doctor,patient,s);
            return new Reply("There are no available times on that date. "+dates.text(),dates.interactive()); }
        return list("Available times on "+date+":","Choose time","Available times",rows);
    }
    private Reply confirm(Doctor doctor, PatientBookingSession s) {
        String type="FOLLOW_UP".equals(s.getAppointmentType())?"Follow-up":(doctor.getSpecialization()==null?"Consultation":doctor.getSpecialization()+" consultation");
        String body="Please confirm your visit:\n"+type+" with Dr. "+doctor.getFullName()+"\n"+s.getAppointmentDate()+" at "+s.getAppointmentTime()
                +(doctor.getConsultationFee()==null?"":"\nFee: "+doctor.getConsultationFee())+"\nPayment is pending until the clinic confirms it.";
        return buttons(body,List.of(btn("BOOK_CONFIRM","Confirm booking"),btn("BOOK_CHANGE","Choose another time")));
    }
    private Reply buttons(String body,List<Map<String,Object>> buttons) { return new Reply(body,Map.of("type","button","body",Map.of("text",body),"action",Map.of("buttons",buttons))); }
    private Map<String,Object> btn(String id,String title) { return Map.of("type","reply","reply",Map.of("id",id,"title",title.length()>20?title.substring(0,20):title)); }
    private Reply list(String body,String button,String section,List<Map<String,Object>> rows) { return new Reply(body,Map.of("type","list","body",Map.of("text",body),"action",Map.of("button",button,"sections",List.of(Map.of("title",section,"rows",rows))))); }
    private Map<String,Object> row(String id,String title,String description) { return Map.of("id",id,"title",title.length()>24?title.substring(0,24):title,"description",description); }
}
