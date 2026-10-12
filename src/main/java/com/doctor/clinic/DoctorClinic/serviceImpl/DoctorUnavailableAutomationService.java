package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDate;
import java.util.Comparator;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DoctorUnavailableAutomationService {
    private final AppointmentRepo appointmentRepo;
    private final DoctorSlotRepo slotRepo;
    private final BookingRequestService bookingRequestService;
    private final WhatsappServiceImpl whatsappService;

    @Transactional
    public int cancelAndOfferReschedule(Doctor doctor, LocalDate date, String reason) {
        if (Boolean.FALSE.equals(doctor.getOrganization().getAppointmentAutomationEnabled())) return 0;
        int processed = 0;
        var slots = slotRepo.findByDoctorIdAndSlotDateBetweenAndIsAvailableTrue(doctor.getId(), date.plusDays(1), date.plusDays(14))
                .stream().filter(DoctorSlot::hasAvailability)
                .sorted(Comparator.comparing(DoctorSlot::getSlotDate).thenComparing(DoctorSlot::getStartTime))
                .toList();
        for (Appointment appointment : appointmentRepo.findByDoctorIdAndAppointmentDate(doctor.getId(), date)) {
            if (!isActive(appointment)) continue;
            appointment.setAppointmentStatus("CANCELLED");
            appointment.setCancellationReason(reason == null || reason.isBlank()
                    ? "Doctor unavailable" : reason);
            appointment.setCancelledBy("SYSTEM");
            appointmentRepo.save(appointment);
            if (appointment.getPatient() != null && !slots.isEmpty()) {
                var request = bookingRequestService.createPending(doctor, appointment.getPatient(),
                        appointment.getAppointmentType(), "DOCTOR_UNAVAILABLE_AUTOMATION",
                        "Doctor unavailable; original appointment cancelled", date, appointment.getAppointmentTime());
                bookingRequestService.offerSlots(request.getId(), doctor.getOrganization().getId(),
                        slots.stream().limit(3).map(DoctorSlot::getId).toList());
            } else if (appointment.getPatientPhone() != null && !appointment.getPatientPhone().isBlank()) {
                whatsappService.sendMessage(doctor, appointment.getPatientPhone(),
                        "Your appointment on " + date + " was cancelled because the doctor is unavailable. "
                                + "Please reply to this WhatsApp message to reschedule.");
            }
            slotRepo.findByDoctorIdAndSlotDateAndStartTime(doctor.getId(), date, appointment.getAppointmentTime())
                    .ifPresent(slot -> {
                        if (slot.getBookedCount() > 0) {
                            slot.setBookedCount(slot.getBookedCount() - 1);
                            slotRepo.save(slot);
                        }
                    });
            processed++;
        }
        return processed;
    }

    private boolean isActive(Appointment appointment) {
        return "SCHEDULED".equals(appointment.getAppointmentStatus())
                || "CONFIRMED".equals(appointment.getAppointmentStatus());
    }
}
