package com.doctor.clinic.DoctorClinic.serviceImpl;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.doctor.clinic.DoctorClinic.entity.PatientChatMessage;
import com.doctor.clinic.DoctorClinic.repo.PatientChatMessageRepo;

@Service
public class WhatsAppLegacyHistoryBackfillService {

    private static final int PAGE_SIZE = 200;

    private final PatientChatMessageRepo patientChatMessageRepo;
    private final WhatsAppConversationServiceImpl conversationService;

    public WhatsAppLegacyHistoryBackfillService(PatientChatMessageRepo patientChatMessageRepo,
                                                WhatsAppConversationServiceImpl conversationService) {
        this.patientChatMessageRepo = patientChatMessageRepo;
        this.conversationService = conversationService;
    }

    @Transactional
    public int backfillAll() {
        int imported = 0;
        int pageNumber = 0;
        Page<PatientChatMessage> page;
        do {
            page = patientChatMessageRepo.findAllByOrderByIdAsc(PageRequest.of(pageNumber++, PAGE_SIZE));
            for (PatientChatMessage legacyMessage : page.getContent()) {
                if (conversationService.recordLegacyMessage(legacyMessage)) {
                    imported++;
                }
            }
        } while (page.hasNext());
        return imported;
    }
}
