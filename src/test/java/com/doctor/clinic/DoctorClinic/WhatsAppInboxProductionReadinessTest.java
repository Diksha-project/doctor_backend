package com.doctor.clinic.DoctorClinic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.security.access.AccessDeniedException;

import com.doctor.clinic.DoctorClinic.AIServices.WhatsappServiceImpl;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Patient;
import com.doctor.clinic.DoctorClinic.entity.PatientChatMessage;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppConversation;
import com.doctor.clinic.DoctorClinic.entity.WhatsAppMessage;
import com.doctor.clinic.DoctorClinic.model.AutomationMode;
import com.doctor.clinic.DoctorClinic.model.ConversationStatus;
import com.doctor.clinic.DoctorClinic.model.MessageDirection;
import com.doctor.clinic.DoctorClinic.model.MessageSenderType;
import com.doctor.clinic.DoctorClinic.model.MessageStatus;
import com.doctor.clinic.DoctorClinic.model.OrganizationType;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientChatMessageRepo;
import com.doctor.clinic.DoctorClinic.repo.ProcessedWhatsappMessageRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppConversationRepo;
import com.doctor.clinic.DoctorClinic.repo.WhatsAppMessageRepo;
import com.doctor.clinic.DoctorClinic.security.JwtUtil;
import com.doctor.clinic.DoctorClinic.config.WebSocketConfig;
import com.doctor.clinic.DoctorClinic.serviceImpl.WhatsAppLegacyHistoryBackfillService;
import com.doctor.clinic.DoctorClinic.serviceImpl.WhatsAppConversationServiceImpl;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
class WhatsAppInboxProductionReadinessTest {

    @Autowired
    private ProcessedWhatsappMessageRepo processedWhatsappMessageRepo;

    @Autowired
    private OrganizationRepo organizationRepo;

    @Autowired
    private DoctorRepo doctorRepo;

    @Autowired
    private PatientRepo patientRepo;

    @Autowired
    private WhatsAppConversationRepo conversationRepo;

    @Autowired
    private WhatsAppConversationServiceImpl conversationService;

    @Autowired
    private WhatsAppLegacyHistoryBackfillService backfillService;

    @Autowired
    private PatientChatMessageRepo patientChatMessageRepo;

    @Autowired
    private WhatsAppMessageRepo whatsappMessageRepo;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private WebSocketConfig webSocketConfig;

    @MockBean
    private WhatsappServiceImpl whatsappService;

    @Test
    void duplicateWebhookMessageIdIsClaimedOnlyOnceAcrossConcurrentRequests() throws Exception {
        String messageId = "meta-dup-" + UUID.randomUUID();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        List<Future<Integer>> futures = new ArrayList<>();
        CountDownLatch startGate = new CountDownLatch(1);

        for (int i = 0; i < 8; i++) {
            futures.add(pool.submit(() -> {
                startGate.await();
                return processedWhatsappMessageRepo.claimMessageId(messageId);
            }));
        }

        startGate.countDown();
        int inserts = 0;
        for (Future<Integer> future : futures) {
            inserts += future.get();
        }

        pool.shutdown();

        assertEquals(1, inserts, "Only one duplicate-safe insert should succeed");
        assertTrue(processedWhatsappMessageRepo.findById(messageId).isPresent());
    }

    @Test
    void humanTakeoverDisablesAiAndResumeRestoresHybridMode() {
        Organization organization = organizationRepo.save(Organization.builder()
                .ownerFullName("Test Owner")
                .ownerMobile("9999999999")
                .ownerEmail("takeover-" + UUID.randomUUID() + "@example.com")
                .organizationName("Takeover Clinic")
                .organizationType(OrganizationType.CLINIC)
                .passwordHash("hashed-password")
                .maxDoctors(2)
                .doctorsCount(0)
                .build());

        Doctor doctor = doctorRepo.save(Doctor.builder()
                .firstName("Dr")
                .lastName("Takeover")
                .registrationNumber("REG-TAKEOVER-" + UUID.randomUUID())
                .qualification("MD")
                .specialization("General Medicine")
                .experienceYears(12)
                .phoneNumber("8888888888")
                .email("doctor-" + UUID.randomUUID() + "@example.com")
                .consultationFee(BigDecimal.valueOf(500))
                .consultationHours("9 AM - 6 PM")
                .organization(organization)
                .build());

        Patient patient = new Patient();
        patient.setOrganization(organization);
        patient.setFullName("Test Patient");
        patient.setNormalizedPhone("919999999999");
        patient = patientRepo.save(patient);

        WhatsAppConversation conversation = new WhatsAppConversation();
        conversation.setOrganization(organization);
        conversation.setPatient(patient);
        conversation.setDoctor(doctor);
        conversation.setPhoneNumber("919999999999");
        conversation.setStatus(ConversationStatus.AI_ACTIVE);
        conversation.setAutomationMode(AutomationMode.HYBRID);
        conversation.setAiEnabled(true);
        conversation.setHumanTakeover(false);
        conversation = conversationRepo.save(conversation);

        conversationService.toggleTakeover(conversation.getId(), organization.getId(), true);
        WhatsAppConversation takeoverState = conversationRepo.findById(conversation.getId()).orElseThrow();
        assertTrue(takeoverState.isHumanTakeover());
        assertFalse(takeoverState.isAiEnabled());
        assertEquals(AutomationMode.MANUAL, takeoverState.getAutomationMode());

        conversationService.toggleTakeover(conversation.getId(), organization.getId(), false);
        WhatsAppConversation resumed = conversationRepo.findById(conversation.getId()).orElseThrow();
        assertFalse(resumed.isHumanTakeover());
        assertTrue(resumed.isAiEnabled());
        assertEquals(AutomationMode.HYBRID, resumed.getAutomationMode());
    }

    @Test
    void legacyPatientWhatsAppHistoryBackfillsIntoInboxIdempotently() {
        TestRecords records = createTestRecords();
        LocalDateTime firstAt = LocalDateTime.of(2025, 1, 2, 10, 0);
        LocalDateTime secondAt = firstAt.plusMinutes(3);

        PatientChatMessage inbound = new PatientChatMessage();
        inbound.setPatient(records.patient());
        inbound.setDoctor(records.doctor());
        inbound.setDirection("INBOUND");
        inbound.setWhatsappMessageId("wamid.legacy-" + UUID.randomUUID());
        inbound.setMessageType("text");
        inbound.setMessageText("Hello clinic");
        inbound.setCreatedAt(firstAt);
        patientChatMessageRepo.save(inbound);

        PatientChatMessage outbound = new PatientChatMessage();
        outbound.setPatient(records.patient());
        outbound.setDoctor(records.doctor());
        outbound.setDirection("OUTBOUND");
        outbound.setReplyToMessageId(inbound.getWhatsappMessageId());
        outbound.setMessageType("text");
        outbound.setMessageText("How can we help?");
        outbound.setCreatedAt(secondAt);
        patientChatMessageRepo.save(outbound);

        assertTrue(backfillService.backfillAll() >= 2);
        assertEquals(0, backfillService.backfillAll());

        WhatsAppConversation conversation = conversationRepo
                .findByOrganizationIdAndPatientIdAndPhoneNumber(
                        records.organization().getId(), records.patient().getId(),
                        "91" + records.patient().getNormalizedPhone())
                .orElseThrow();
        List<WhatsAppMessage> messages = whatsappMessageRepo
                .findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(
                        records.organization().getId(), conversation.getId());
        List<WhatsAppMessage> migrated = messages.stream()
                .filter(message -> "Hello clinic".equals(message.getContent())
                        || "How can we help?".equals(message.getContent()))
                .toList();
        assertEquals(2, migrated.size());
        assertEquals(firstAt, migrated.get(0).getCreatedAt());
        assertEquals("Hello clinic", migrated.get(0).getContent());
        assertEquals(MessageDirection.INBOUND, migrated.get(0).getDirection());
        assertEquals(secondAt, migrated.get(1).getCreatedAt());
        assertEquals("How can we help?", migrated.get(1).getContent());
        assertEquals(MessageDirection.OUTBOUND, migrated.get(1).getDirection());
    }

    @Test
    void inboundMessagesReuseConversationAndMetaIdempotency() {
        TestRecords records = createTestRecords();
        String phone = "91" + records.patient().getNormalizedPhone();
        String messageId = "wamid.live-" + UUID.randomUUID();

        WhatsAppMessage first = conversationService.recordInboundMessage(
                records.organization().getId(), records.patient(), records.doctor(),
                phone, messageId, "Patient hello", "text");
        WhatsAppMessage duplicate = conversationService.recordInboundMessage(
                records.organization().getId(), records.patient(), records.doctor(),
                phone, messageId, "Patient hello", "text");
        conversationService.recordInboundMessage(
                records.organization().getId(), records.patient(), records.doctor(),
                phone, "wamid.live-" + UUID.randomUUID(), "Second message", "text");

        assertEquals(first.getId(), duplicate.getId());
        assertEquals(1, conversationRepo.findByOrganizationIdAndPatientIdAndPhoneNumber(
                records.organization().getId(), records.patient().getId(), phone).stream().count());
        assertEquals(2, whatsappMessageRepo.findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(
                records.organization().getId(), first.getConversation().getId()).size());
    }

    @Test
    void manualAndAiOutboundMessagesHaveCorrectInboxFlagsAndStatusCanUpdate() {
        TestRecords records = createTestRecords();
        WhatsAppConversation conversation = conversationService.upsertConversation(
                records.organization().getId(), records.patient(),
                "91" + records.patient().getNormalizedPhone(), records.doctor());
        String manualProviderId = "wamid.manual-" + UUID.randomUUID();
        when(whatsappService.sendMessageWithResult(any(Doctor.class), anyString(), anyString()))
                .thenReturn(new WhatsappServiceImpl.SendResult(true, manualProviderId));

        conversationService.sendMessage(conversation.getId(), records.organization().getId(), "Staff reply", "TEXT");
        WhatsAppMessage manual = whatsappMessageRepo.findByMetaMessageId(manualProviderId).orElseThrow();
        assertTrue(manual.isManual());
        assertFalse(manual.isAiGenerated());
        assertEquals(MessageSenderType.ADMIN, manual.getSenderType());

        String aiProviderId = "wamid.ai-" + UUID.randomUUID();
        when(whatsappService.sendMessageWithResult(any(Doctor.class), anyString(), anyString()))
                .thenReturn(new WhatsappServiceImpl.SendResult(true, aiProviderId));
        assertTrue(conversationService.sendAutomatedMessage(
                records.organization().getId(), records.patient(), records.doctor(),
                "91" + records.patient().getNormalizedPhone(), "Automated reply"));
        WhatsAppMessage ai = whatsappMessageRepo.findByMetaMessageId(aiProviderId).orElseThrow();
        assertTrue(ai.isAiGenerated());
        assertFalse(ai.isManual());
        assertEquals(MessageSenderType.AI, ai.getSenderType());

        assertTrue(conversationService.updateDeliveryStatus(aiProviderId, "delivered",
                LocalDateTime.now(), null));
        assertEquals(MessageStatus.DELIVERED,
                whatsappMessageRepo.findById(ai.getId()).orElseThrow().getStatus());
    }

    @Test
    void anotherOrganizationCannotReadConversation() {
        TestRecords records = createTestRecords();
        WhatsAppConversation conversation = conversationService.upsertConversation(
                records.organization().getId(), records.patient(),
                "91" + records.patient().getNormalizedPhone(), records.doctor());
        Organization otherOrganization = createOrganization();

        assertThrows(IllegalArgumentException.class,
                () -> conversationService.getConversation(conversation.getId(), otherOrganization.getId()));
    }

    @Test
    void websocketConnectAuthenticatesJwtAndSubscribeRejectsOtherOrganization() {
        String token = jwtUtil.generateToken("inbox@example.com", "ADMIN", 41L);
        StompHeaderAccessor connectAccessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        connectAccessor.setLeaveMutable(true);
        connectAccessor.setNativeHeader("Authorization", "Bearer " + token);
        Message<byte[]> connectMessage = MessageBuilder.createMessage(new byte[0], connectAccessor.getMessageHeaders());

        Message<?> authenticated = webSocketConfig.stompAuthInterceptor().preSend(connectMessage, null);
        StompHeaderAccessor authenticatedAccessor =
                StompHeaderAccessor.wrap(authenticated);
        assertTrue(authenticatedAccessor.getUser() != null);

        StompHeaderAccessor authorizedSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        authorizedSub.setLeaveMutable(true);
        authorizedSub.setUser(authenticatedAccessor.getUser());
        authorizedSub.setDestination("/topic/organizations/41/whatsapp/messages");
        webSocketConfig.stompAuthInterceptor().preSend(
                MessageBuilder.createMessage(new byte[0], authorizedSub.getMessageHeaders()), null);

        StompHeaderAccessor crossTenantSub = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        crossTenantSub.setLeaveMutable(true);
        crossTenantSub.setUser(authenticatedAccessor.getUser());
        crossTenantSub.setDestination("/topic/organizations/42/whatsapp/messages");
        assertThrows(AccessDeniedException.class, () -> webSocketConfig.stompAuthInterceptor().preSend(
                MessageBuilder.createMessage(new byte[0], crossTenantSub.getMessageHeaders()), null));
    }

    private TestRecords createTestRecords() {
        Organization organization = createOrganization();
        Doctor doctor = doctorRepo.save(Doctor.builder()
                .firstName("Dr")
                .lastName("Inbox")
                .registrationNumber("REG-" + UUID.randomUUID())
                .qualification("MD")
                .specialization("General Medicine")
                .experienceYears(12)
                .phoneNumber(uniquePhone("8"))
                .email("doctor-" + UUID.randomUUID() + "@example.com")
                .consultationFee(BigDecimal.valueOf(500))
                .consultationHours("9 AM - 6 PM")
                .organization(organization)
                .build());
        Patient patient = new Patient();
        patient.setOrganization(organization);
        patient.setFullName("Inbox Patient");
        patient.setNormalizedPhone(uniquePhone("9"));
        patient = patientRepo.save(patient);
        return new TestRecords(organization, doctor, patient);
    }

    private Organization createOrganization() {
        return organizationRepo.save(Organization.builder()
                .ownerFullName("Test Owner")
                .ownerMobile(uniquePhone("9"))
                .ownerEmail("owner-" + UUID.randomUUID() + "@example.com")
                .organizationName("Clinic " + UUID.randomUUID())
                .organizationType(OrganizationType.CLINIC)
                .passwordHash("hashed-password")
                .maxDoctors(2)
                .doctorsCount(0)
                .build());
    }

    private String uniquePhone(String prefix) {
        return prefix + String.format("%09d", Math.floorMod(System.nanoTime(), 1_000_000_000L));
    }

    private record TestRecords(Organization organization, Doctor doctor, Patient patient) {
    }
}
