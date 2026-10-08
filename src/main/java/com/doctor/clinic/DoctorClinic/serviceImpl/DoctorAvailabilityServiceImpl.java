package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.DoctorAvailability;
import com.doctor.clinic.DoctorClinic.entity.DoctorAvailabilityException;
import com.doctor.clinic.DoctorClinic.entity.DoctorAvailabilitySettings;
import com.doctor.clinic.DoctorClinic.entity.DoctorSlot;
import com.doctor.clinic.DoctorClinic.model.AvailabilityExceptionType;
import com.doctor.clinic.DoctorClinic.repo.DoctorAvailabilityExceptionRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorAvailabilityRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorAvailabilitySettingsRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorSlotRepo;
import com.doctor.clinic.DoctorClinic.request.AvailabilityExceptionRequest;
import com.doctor.clinic.DoctorClinic.request.AvailabilityPeriodRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateAvailabilitySettingsRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateWeeklyAvailabilityRequest;
import com.doctor.clinic.DoctorClinic.response.AvailabilityExceptionResponse;
import com.doctor.clinic.DoctorClinic.response.AvailabilityPeriodResponse;
import com.doctor.clinic.DoctorClinic.response.DoctorAvailabilityResponse;
import com.doctor.clinic.DoctorClinic.response.SlotGenerationResultResponse;
import com.doctor.clinic.DoctorClinic.service.DoctorAvailabilityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorAvailabilityServiceImpl implements DoctorAvailabilityService {

    private static final int DEFAULT_APPOINTMENT_DURATION_MINUTES = 30;
    private static final int DEFAULT_MAX_APPOINTMENTS_PER_DAY = 20;
    private static final int DEFAULT_SLOT_GENERATION_HORIZON_DAYS = 30;

    private final DoctorRepo doctorRepo;
    private final DoctorAvailabilityRepo availabilityRepo;
    private final DoctorAvailabilitySettingsRepo settingsRepo;
    private final DoctorAvailabilityExceptionRepo exceptionRepo;
    private final DoctorSlotRepo doctorSlotRepo;

    @Override
    @Transactional(readOnly = true)
    public DoctorAvailabilityResponse getAvailability(Long doctorId, Long organizationId) {
        Doctor doctor = requireDoctor(doctorId, organizationId);
        return buildResponse(doctor);
    }

    @Override
    @Transactional
    public DoctorAvailabilityResponse updateWeeklySchedule(Long doctorId, Long organizationId,
            UpdateWeeklyAvailabilityRequest request) {
        Doctor doctor = requireDoctor(doctorId, organizationId);

        for (AvailabilityPeriodRequest period : request.getPeriods()) {
            if (!period.getStartTime().isBefore(period.getEndTime())) {
                throw new BusinessException(400, "INVALID_PERIOD",
                        "startTime must be before endTime for " + period.getDayOfWeek());
            }
        }

        availabilityRepo.deleteByDoctorId(doctorId);
        List<DoctorAvailability> saved = request.getPeriods().stream()
                .map(period -> DoctorAvailability.builder()
                        .doctor(doctor)
                        .organization(doctor.getOrganization())
                        .dayOfWeek(period.getDayOfWeek())
                        .startTime(period.getStartTime())
                        .endTime(period.getEndTime())
                        .active(true)
                        .build())
                .collect(Collectors.toList());
        availabilityRepo.saveAll(saved);

        generateSlots(doctorId);
        return buildResponse(doctor);
    }

    @Override
    @Transactional
    public DoctorAvailabilityResponse updateSettings(Long doctorId, Long organizationId,
            UpdateAvailabilitySettingsRequest request) {
        Doctor doctor = requireDoctor(doctorId, organizationId);

        DoctorAvailabilitySettings settings = settingsRepo.findByDoctorId(doctorId)
                .orElseGet(() -> DoctorAvailabilitySettings.builder().doctor(doctor).build());
        settings.setDoctor(doctor);
        settings.setAppointmentDurationMinutes(request.getAppointmentDurationMinutes());
        settings.setMaxAppointmentsPerDay(request.getMaxAppointmentsPerDay());
        if (request.getSlotGenerationHorizonDays() != null) {
            settings.setSlotGenerationHorizonDays(request.getSlotGenerationHorizonDays());
        }
        settingsRepo.save(settings);

        generateSlots(doctorId);
        return buildResponse(doctor);
    }

    @Override
    @Transactional
    public DoctorAvailabilityResponse addOrUpdateException(Long doctorId, Long organizationId,
            AvailabilityExceptionRequest request) {
        Doctor doctor = requireDoctor(doctorId, organizationId);

        if (request.getType() == AvailabilityExceptionType.CUSTOM_HOURS
                && (request.getStartTime() == null || request.getEndTime() == null)) {
            throw new BusinessException(400, "INVALID_EXCEPTION",
                    "startTime and endTime are required for CUSTOM_HOURS exceptions");
        }
        if (request.getType() == AvailabilityExceptionType.CUSTOM_HOURS
                && !request.getStartTime().isBefore(request.getEndTime())) {
            throw new BusinessException(400, "INVALID_EXCEPTION", "startTime must be before endTime");
        }
        if (request.getType() == AvailabilityExceptionType.CUSTOM_LIMIT
                && (request.getMaxAppointments() == null || request.getMaxAppointments() < 0)) {
            throw new BusinessException(400, "INVALID_EXCEPTION",
                    "maxAppointments is required and must be >= 0 for CUSTOM_LIMIT exceptions");
        }

        exceptionRepo.deleteByDoctorIdAndExceptionDate(doctorId, request.getDate());
        DoctorAvailabilityException exception = DoctorAvailabilityException.builder()
                .doctor(doctor)
                .organization(doctor.getOrganization())
                .exceptionDate(request.getDate())
                .exceptionType(request.getType())
                .startTime(request.getStartTime())
                .endTime(request.getEndTime())
                .maxAppointments(request.getMaxAppointments())
                .reason(request.getReason())
                .build();
        exceptionRepo.save(exception);

        generateSlots(doctorId);
        return buildResponse(doctor);
    }

    @Override
    @Transactional
    public DoctorAvailabilityResponse removeException(Long doctorId, Long organizationId, LocalDate date) {
        Doctor doctor = requireDoctor(doctorId, organizationId);
        exceptionRepo.deleteByDoctorIdAndExceptionDate(doctorId, date);
        generateSlots(doctorId);
        return buildResponse(doctor);
    }

    @Override
    @Transactional
    public SlotGenerationResultResponse generateSlots(Long doctorId) {
        Doctor doctor = doctorRepo.findById(doctorId)
                .orElseThrow(() -> BusinessException.notFound("Doctor", doctorId));

        DoctorAvailabilitySettings settings = settingsRepo.findByDoctorId(doctorId)
                .orElseGet(() -> DoctorAvailabilitySettings.builder()
                        .doctor(doctor)
                        .appointmentDurationMinutes(DEFAULT_APPOINTMENT_DURATION_MINUTES)
                        .maxAppointmentsPerDay(DEFAULT_MAX_APPOINTMENTS_PER_DAY)
                        .slotGenerationHorizonDays(DEFAULT_SLOT_GENERATION_HORIZON_DAYS)
                        .build());
        int durationMinutes = settings.getAppointmentDurationMinutes();
        int horizonDays = settings.getSlotGenerationHorizonDays();

        LocalDate today = LocalDate.now();
        LocalDate horizonEnd = today.plusDays(horizonDays);

        Map<DayOfWeek, List<DoctorAvailability>> weeklyByDay = availabilityRepo
                .findByDoctorIdAndActiveTrueOrderByDayOfWeekAscStartTimeAsc(doctorId).stream()
                .collect(Collectors.groupingBy(DoctorAvailability::getDayOfWeek));

        Map<LocalDate, DoctorAvailabilityException> exceptionsByDate = exceptionRepo
                .findByDoctorIdAndExceptionDateBetweenOrderByExceptionDateAsc(doctorId, today, horizonEnd).stream()
                .collect(Collectors.toMap(DoctorAvailabilityException::getExceptionDate, e -> e));

        Map<LocalDate, List<DoctorSlot>> existingByDate = doctorSlotRepo
                .findByDoctorIdAndSlotDateBetween(doctorId, today, horizonEnd).stream()
                .collect(Collectors.groupingBy(DoctorSlot::getSlotDate));

        int created = 0;
        int removed = 0;
        int daysProcessed = 0;

        for (LocalDate date = today; !date.isAfter(horizonEnd); date = date.plusDays(1)) {
            daysProcessed++;
            DoctorAvailabilityException exception = exceptionsByDate.get(date);
            List<EffectivePeriod> effectivePeriods = resolveEffectivePeriods(date, exception, weeklyByDay);

            List<LocalTime> expectedStartTimes = new ArrayList<>();
            for (EffectivePeriod period : effectivePeriods) {
                LocalTime start = period.start;
                while (!start.plusMinutes(durationMinutes).isAfter(period.end)) {
                    expectedStartTimes.add(start);
                    start = start.plusMinutes(durationMinutes);
                }
            }

            Map<LocalTime, DoctorSlot> existingSlots = existingByDate
                    .getOrDefault(date, Collections.emptyList()).stream()
                    .collect(Collectors.toMap(DoctorSlot::getStartTime, s -> s, (a, b) -> a));

            for (LocalTime startTime : expectedStartTimes) {
                if (!existingSlots.containsKey(startTime)) {
                    DoctorSlot newSlot = DoctorSlot.builder()
                            .doctor(doctor)
                            .organization(doctor.getOrganization())
                            .slotDate(date)
                            .startTime(startTime)
                            .endTime(startTime.plusMinutes(durationMinutes))
                            .durationMinutes(durationMinutes)
                            .isAvailable(true)
                            .maxAppointments(1)
                            .bookedCount(0)
                            .slotType("REGULAR")
                            .build();
                    doctorSlotRepo.save(newSlot);
                    created++;
                }
            }

            List<LocalTime> expectedSet = expectedStartTimes;
            for (DoctorSlot existingSlot : existingSlots.values()) {
                boolean stillExpected = expectedSet.contains(existingSlot.getStartTime());
                boolean safeToRemove = existingSlot.getBookedCount() == null || existingSlot.getBookedCount() == 0;
                if (!stillExpected && safeToRemove) {
                    doctorSlotRepo.delete(existingSlot);
                    removed++;
                }
            }
        }

        log.info("Slot generation for doctor {}: {} days processed, {} created, {} removed", doctorId, daysProcessed,
                created, removed);

        return SlotGenerationResultResponse.builder()
                .doctorId(doctorId)
                .slotsCreated(created)
                .slotsRemoved(removed)
                .daysProcessed(daysProcessed)
                .build();
    }

    @Override
    @Transactional
    public List<SlotGenerationResultResponse> generateSlotsForAllActiveDoctors() {
        List<Doctor> activeDoctors = doctorRepo.findByStatus("ACTIVE");
        List<SlotGenerationResultResponse> results = new ArrayList<>();
        for (Doctor doctor : activeDoctors) {
            try {
                results.add(generateSlots(doctor.getId()));
            } catch (Exception e) {
                log.error("Slot generation failed for doctor {}: {}", doctor.getId(), e.getMessage(), e);
            }
        }
        return results;
    }

    private List<EffectivePeriod> resolveEffectivePeriods(LocalDate date, DoctorAvailabilityException exception,
            Map<DayOfWeek, List<DoctorAvailability>> weeklyByDay) {
        if (exception != null && exception.getExceptionType() == AvailabilityExceptionType.DAY_OFF) {
            return Collections.emptyList();
        }
        if (exception != null && exception.getExceptionType() == AvailabilityExceptionType.CUSTOM_HOURS) {
            return List.of(new EffectivePeriod(exception.getStartTime(), exception.getEndTime()));
        }
        // CUSTOM_LIMIT (or no exception): fall back to the normal weekly template.
        return weeklyByDay.getOrDefault(date.getDayOfWeek(), Collections.emptyList()).stream()
                .map(a -> new EffectivePeriod(a.getStartTime(), a.getEndTime()))
                .collect(Collectors.toList());
    }

    private Doctor requireDoctor(Long doctorId, Long organizationId) {
        return doctorRepo.findByIdAndOrganizationId(doctorId, organizationId)
                .orElseThrow(() -> BusinessException.notFound("Doctor", doctorId));
    }

    private DoctorAvailabilityResponse buildResponse(Doctor doctor) {
        Long doctorId = doctor.getId();
        DoctorAvailabilitySettings settings = settingsRepo.findByDoctorId(doctorId).orElse(null);

        List<AvailabilityPeriodResponse> weeklySchedule = availabilityRepo
                .findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctorId).stream()
                .map(a -> AvailabilityPeriodResponse.builder()
                        .id(a.getId())
                        .dayOfWeek(a.getDayOfWeek())
                        .startTime(a.getStartTime())
                        .endTime(a.getEndTime())
                        .active(a.isActive())
                        .build())
                .collect(Collectors.toList());

        List<AvailabilityExceptionResponse> exceptions = exceptionRepo.findByDoctorIdOrderByExceptionDateAsc(doctorId)
                .stream()
                .filter(e -> !e.getExceptionDate().isBefore(LocalDate.now()))
                .map(e -> AvailabilityExceptionResponse.builder()
                        .id(e.getId())
                        .date(e.getExceptionDate())
                        .type(e.getExceptionType())
                        .startTime(e.getStartTime())
                        .endTime(e.getEndTime())
                        .maxAppointments(e.getMaxAppointments())
                        .reason(e.getReason())
                        .build())
                .collect(Collectors.toList());

        return DoctorAvailabilityResponse.builder()
                .doctorId(doctorId)
                .appointmentDurationMinutes(settings != null ? settings.getAppointmentDurationMinutes()
                        : DEFAULT_APPOINTMENT_DURATION_MINUTES)
                .maxAppointmentsPerDay(
                        settings != null ? settings.getMaxAppointmentsPerDay() : DEFAULT_MAX_APPOINTMENTS_PER_DAY)
                .slotGenerationHorizonDays(settings != null ? settings.getSlotGenerationHorizonDays()
                        : DEFAULT_SLOT_GENERATION_HORIZON_DAYS)
                .weeklySchedule(weeklySchedule)
                .exceptions(exceptions)
                .build();
    }

    private static final class EffectivePeriod {
        private final LocalTime start;
        private final LocalTime end;

        private EffectivePeriod(LocalTime start, LocalTime end) {
            this.start = start;
            this.end = end;
        }
    }
}
