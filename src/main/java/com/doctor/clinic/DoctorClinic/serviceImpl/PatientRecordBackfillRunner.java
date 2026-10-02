package com.doctor.clinic.DoctorClinic.serviceImpl;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.doctor.clinic.DoctorClinic.entity.Appointment;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class PatientRecordBackfillRunner implements ApplicationRunner {

    private final AppointmentRepo appointmentRepo;
    private final PatientProfileService patientProfileService;

    public PatientRecordBackfillRunner(AppointmentRepo appointmentRepo,
                                       PatientProfileService patientProfileService) {
        this.appointmentRepo = appointmentRepo;
        this.patientProfileService = patientProfileService;
    }

    @Override
    public void run(ApplicationArguments args) {
        long cursor = 0;
        int linked = 0;
        while (true) {
            var batch = appointmentRepo.findTop100ByPatientIsNullAndIdGreaterThanOrderByIdAsc(cursor);
            if (batch.isEmpty()) break;

            for (Appointment appointment : batch) {
                cursor = appointment.getId();
                if (appointment.getPatientPhone() == null || appointment.getPatientPhone().isBlank()
                        || appointment.getOrganization() == null) {
                    continue;
                }
                try {
                    Patient patient = patientProfileService.upsert(appointment.getOrganization(),
                            appointment.getPatientPhone(), appointment.getPatientName(),
                            appointment.getPatientEmail(), appointment.getPatientAge(),
                            appointment.getPatientGender());
                    appointment.setPatient(patient);
                    appointmentRepo.save(appointment);
                    linked++;
                } catch (Exception e) {
                    log.warn("Could not link legacy appointment {} to a patient profile: {}",
                            appointment.getId(), e.getMessage());
                }
            }
        }
        if (linked > 0) log.info("Linked {} legacy appointments to organization-scoped patient profiles", linked);
    }
}
