package com.doctor.clinic.DoctorClinic.scheduler;

import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.doctor.clinic.DoctorClinic.response.SlotGenerationResultResponse;
import com.doctor.clinic.DoctorClinic.service.DoctorAvailabilityService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Keeps every active doctor's rolling slot-generation window topped up, so
 * bookable slots never run out even if a doctor never revisits their
 * availability settings.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SlotGenerationScheduler {

    private final DoctorAvailabilityService doctorAvailabilityService;

    @Scheduled(cron = "0 0 2 * * *")
    public void regenerateSlotsForAllDoctors() {
        log.info("Running nightly slot generation for all active doctors");
        List<SlotGenerationResultResponse> results = doctorAvailabilityService.generateSlotsForAllActiveDoctors();
        int created = results.stream().mapToInt(SlotGenerationResultResponse::getSlotsCreated).sum();
        int removed = results.stream().mapToInt(SlotGenerationResultResponse::getSlotsRemoved).sum();
        log.info("Nightly slot generation complete for {} doctors: {} created, {} removed", results.size(), created,
                removed);
    }
}
