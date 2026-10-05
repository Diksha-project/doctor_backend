package com.doctor.clinic.DoctorClinic.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.doctor.clinic.DoctorClinic.serviceImpl.WhatsAppLegacyHistoryBackfillService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@ConditionalOnProperty(name = "whatsapp.inbox.legacy-backfill.enabled", havingValue = "true")
public class WhatsAppLegacyHistoryBackfillRunner implements ApplicationRunner {

    private final WhatsAppLegacyHistoryBackfillService backfillService;

    public WhatsAppLegacyHistoryBackfillRunner(WhatsAppLegacyHistoryBackfillService backfillService) {
        this.backfillService = backfillService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int imported = backfillService.backfillAll();
        log.info("WhatsApp inbox legacy history backfill completed; imported {} messages", imported);
    }
}
