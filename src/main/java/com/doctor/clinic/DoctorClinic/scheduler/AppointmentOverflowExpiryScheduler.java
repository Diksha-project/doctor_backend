package com.doctor.clinic.DoctorClinic.scheduler;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import com.doctor.clinic.DoctorClinic.serviceImpl.AppointmentOverflowService;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentOverflowExpiryScheduler {
    private final AppointmentOverflowService service;
    private final OrganizationRepo organizationRepo;
    private volatile Instant nextAutomaticCheck = Instant.MIN;

    @Scheduled(fixedDelay = 60000)
    public void expireRequests() {
        int expired = service.expirePending();
        if (expired > 0) log.info("Expired {} appointment overflow approval request(s)", expired);
    }

    @Scheduled(fixedDelay = 60000)
    public void createNextDayRequests() {
        if (Instant.now().isBefore(nextAutomaticCheck)) return;
        int interval = Math.max(1, organizationRepo.findMinimumOverflowCheckIntervalMinutes());
        nextAutomaticCheck = Instant.now().plus(interval, ChronoUnit.MINUTES);
        int created = service.createAutomaticNextDayRequests();
        if (created > 0) log.info("Created {} automatic next-day capacity request(s)", created);
    }
}
