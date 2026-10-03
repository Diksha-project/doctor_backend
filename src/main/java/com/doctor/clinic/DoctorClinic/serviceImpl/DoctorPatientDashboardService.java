package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientChatMessageRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.response.DoctorPatientDashboardResponse;
import com.doctor.clinic.DoctorClinic.response.DoctorPatientDashboardResponse.AppointmentPatient;
import com.doctor.clinic.DoctorClinic.response.DoctorPatientDashboardResponse.Patient;
import com.doctor.clinic.DoctorClinic.response.DoctorPatientDashboardResponse.PreviousVisit;
import com.doctor.clinic.DoctorClinic.response.DoctorPatientDashboardResponse.RecentMessage;
import com.doctor.clinic.DoctorClinic.serviceImpl.PatientProfileService;

@Service
public class DoctorPatientDashboardService {

    private final DoctorRepo doctorRepo;
    private final AppointmentRepo appointmentRepo;
    private final PatientRepo patientRepo;
    private final PatientChatMessageRepo chatMessageRepo;

    public DoctorPatientDashboardService(DoctorRepo doctorRepo, AppointmentRepo appointmentRepo,
                                         PatientRepo patientRepo, PatientChatMessageRepo chatMessageRepo) {
        this.doctorRepo = doctorRepo;
        this.appointmentRepo = appointmentRepo;
        this.patientRepo = patientRepo;
        this.chatMessageRepo = chatMessageRepo;
    }

    @Transactional(readOnly = true)
    public DoctorPatientDashboardResponse getPatientsForDate(Long doctorId, Long organizationId, LocalDate date) {
        Doctor doctor = doctorRepo.findByIdAndOrganizationId(doctorId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));

        List<AppointmentPatient> patients = appointmentRepo
                .findByDoctorIdAndOrganizationIdAndAppointmentDateOrderByAppointmentTimeAsc(
                        doctorId, organizationId, date)
                .stream().map(appointment -> mapAppointment(appointment, organizationId)).toList();

        return new DoctorPatientDashboardResponse(doctor.getId(), doctor.getFullName(), organizationId, date, patients);
    }

    private AppointmentPatient mapAppointment(Appointment appointment, Long organizationId) {
        com.doctor.clinic.DoctorClinic.entity.Patient patientRecord = appointment.getPatient();
        if (patientRecord == null) {
            String normalizedPhone = PatientProfileService.normalizePhone(appointment.getPatientPhone());
            patientRecord = patientRepo.findByOrganizationIdAndNormalizedPhone(organizationId, normalizedPhone)
                    .orElse(null);
        }

        Patient patient = patientRecord == null
                ? new Patient(null, appointment.getPatientName(), appointment.getPatientPhone(),
                        appointment.getPatientEmail(), appointment.getPatientAge(), appointment.getPatientGender())
                : new Patient(patientRecord.getId(), patientRecord.getFullName(), patientRecord.getNormalizedPhone(),
                        patientRecord.getEmail(), patientRecord.getAge(), patientRecord.getGender());

        List<PreviousVisit> previousVisits = List.of();
        List<RecentMessage> recentMessages = List.of();
        if (patientRecord != null) {
            Long resolvedPatientId = patientRecord.getId();
            List<Appointment> previous = appointmentRepo.findPreviousPatientVisits(organizationId,
                    resolvedPatientId, phoneAliases(patientRecord.getNormalizedPhone()),
                    appointment.getAppointmentDate(), appointment.getAppointmentTime(), PageRequest.of(0, 5));
            previousVisits = previous.stream().map(a -> new PreviousVisit(a.getId(), a.getAppointmentDate(),
                    a.getAppointmentTime(), a.getAppointmentStatus(), a.getReasonForVisit(),
                    a.getSymptoms(), a.getNotes())).toList();

            recentMessages = chatMessageRepo.findRecentTimelineByPatientId(resolvedPatientId, PageRequest.of(0, 8))
                    .stream().map(message -> new RecentMessage(message.getId(), message.getDirection(),
                            message.getMessageType(), message.getMessageText(), message.getAttachmentMimeType(),
                            message.getAttachmentName(), Boolean.TRUE.equals(message.getHasAttachment())
                                    ? "/api/patients/" + resolvedPatientId + "/messages/"
                                            + message.getId() + "/attachment"
                                    : null,
                            message.getCreatedAt())).toList();
        }

        return new AppointmentPatient(appointment.getId(), appointment.getAppointmentDate(),
                appointment.getAppointmentTime(), appointment.getEndTime(), appointment.getSlotDurationMinutes(),
                appointment.getAppointmentStatus(), appointment.getAppointmentType(), appointment.getReasonForVisit(),
                appointment.getSymptoms(), appointment.getNotes(), appointment.getConsultationFee(),
                appointment.getPaymentStatus(), patient, previousVisits, recentMessages);
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
