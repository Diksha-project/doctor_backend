package com.doctor.clinic.DoctorClinic.controller;

import com.doctor.clinic.DoctorClinic.entity.*;
import com.doctor.clinic.DoctorClinic.model.SubscriptionStatus;
import com.doctor.clinic.DoctorClinic.repo.*;
import com.doctor.clinic.DoctorClinic.request.PlatformOrganizationOnboardingRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.response.DemoRequestResponse;
import com.doctor.clinic.DoctorClinic.response.PlatformOnboardingResponse;
import com.doctor.clinic.DoctorClinic.security.PlatformAdminAccessService;
import com.doctor.clinic.DoctorClinic.service.DemoRequestService;
import com.doctor.clinic.DoctorClinic.service.PlatformOnboardingService;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/platform")
@RequiredArgsConstructor
public class PlatformAdminController {
    private static final Set<String> PLANS = Set.of("BASIC", "PROFESSIONAL", "ENTERPRISE");
    private static final Set<String> BILLING_CYCLES = Set.of("MONTHLY", "YEARLY");

    private final PlatformAdminAccessService access;
    private final DemoRequestService demoRequestService;
    private final PlatformOnboardingService onboardingService;
    private final OrganizationRepo organizations;
    private final DoctorRepo doctors;
    private final SubscriptionRepo subscriptions;
    private final AppUserRepo users;

    public record Overview(long organizations, long activeOrganizations, long doctors, long newDemoRequests,
                           long activeSubscriptions) {
    }

    public record OrganizationView(Long id, String name, String type, String ownerName, String email, String phone,
                                   boolean active, String subscriptionStatus, Integer maxDoctors, long doctors,
                                   LocalDate trialEndsAt,
                                   LocalDateTime createdAt) {
    }

    public record DoctorView(Long userId, Long organizationId, String organizationName, String name, String email,
                             String phone, String specialization, String status, boolean accessEnabled) {
    }

    public record AccessUpdate(@NotNull Boolean active) {
    }

    public record SubscriptionInput(@NotBlank String planType, @NotBlank String billingCycle,
                                    @NotBlank String status, LocalDate startDate, LocalDate endDate,
                                    LocalDate trialEndDate,
                                    @Min(1) Integer maxDoctors, @DecimalMin("0") BigDecimal amount, Boolean autoRenew) {
    }

    public record SubscriptionView(Long id, Long organizationId, String planType, String billingCycle,
                                   String status, LocalDate startDate, LocalDate endDate, LocalDate trialEndDate,
                                   Integer maxDoctors, BigDecimal amount, Boolean autoRenew) {
    }

    @GetMapping("/overview")
    public Overview overview() {
        access.requirePlatformAdmin();
        return new Overview(organizations.count(), organizations.countByIsActiveTrue(), doctors.count(),
                demoRequestService.countByStatus("NEW"), subscriptions.countByStatus("ACTIVE"));
    }

    @GetMapping("/demo-requests")
    public List<DemoRequestResponse> demoRequests() {
        access.requirePlatformAdmin();
        return demoRequestService.listAll();
    }

    @PutMapping("/demo-requests/{id}")
    public DemoRequestResponse updateDemo(@PathVariable Long id,
                                          @Valid @RequestBody UpdateDemoRequestRequest input) {
        access.requirePlatformAdmin();
        return demoRequestService.update(id, input);
    }

    @PostMapping("/onboarding/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    public PlatformOnboardingResponse onboardOrganization(
            @Valid @RequestBody PlatformOrganizationOnboardingRequest input) {
        access.requirePlatformAdmin();
        return onboardingService.onboardOrganization(input);
    }

    @GetMapping("/organizations")
    public List<OrganizationView> organizations() {
        access.requirePlatformAdmin();
        return organizations.findAllByOrderByCreatedAtDesc().stream().map(this::organizationView).toList();
    }

    @PutMapping("/organizations/{id}/access")
    @Transactional
    public OrganizationView organizationAccess(@PathVariable Long id, @Valid @RequestBody AccessUpdate input) {
        access.requirePlatformAdmin();
        Organization organization = organizations.findById(id).orElseThrow(this::missing);
        organization.setIsActive(input.active());
        users.findByOrganizationIdOrderByFullNameAsc(id).forEach(user -> user.setActive(input.active()));
        return organizationView(organizations.save(organization));
    }

    @GetMapping("/doctors")
    public List<DoctorView> doctors(@RequestParam(required = false) Long organizationId) {
        access.requirePlatformAdmin();
        List<Doctor> values = organizationId == null ? doctors.findAll() : doctors.findByOrganizationId(organizationId);
        return values.stream().sorted(Comparator.comparing(Doctor::getCreatedAt).reversed()).map(this::doctorView).toList();
    }

    @PutMapping("/doctors/{userId}/access")
    @Transactional
    public DoctorView doctorAccess(@PathVariable Long userId, @Valid @RequestBody AccessUpdate input) {
        access.requirePlatformAdmin();
        Doctor doctor = doctors.findByUserId(userId).orElseThrow(this::missing);
        doctor.setStatus(input.active() ? "ACTIVE" : "INACTIVE");
        doctor.getUser().setActive(input.active());
        return doctorView(doctors.save(doctor));
    }

    @PutMapping("/organizations/{id}/subscription")
    @Transactional
    public SubscriptionView subscription(@PathVariable Long id, @Valid @RequestBody SubscriptionInput input) {
        access.requirePlatformAdmin();
        Organization organization = organizations.findById(id).orElseThrow(this::missing);
        String plan = input.planType().trim().toUpperCase(Locale.ROOT);
        String cycle = input.billingCycle().trim().toUpperCase(Locale.ROOT);
        if (!PLANS.contains(plan)) bad("Unsupported plan");
        if (!BILLING_CYCLES.contains(cycle)) bad("Unsupported billing cycle");
        SubscriptionStatus status;
        try {
            status = SubscriptionStatus.valueOf(input.status().trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw bad("Unsupported subscription status");
        }
        Subscription subscription = subscriptions.findFirstByOrganizationIdOrderByCreatedAtDesc(id).orElseGet(Subscription::new);
        subscription.setOrganization(organization);
        subscription.setPlanType(plan);
        subscription.setBillingCycle(cycle);
        subscription.setStatus(status.name());
        subscription.setStartDate(input.startDate() == null ? LocalDate.now() : input.startDate());
        subscription.setEndDate(input.endDate());
        subscription.setTrialEndDate(input.trialEndDate());
        subscription.setMaxDoctors(input.maxDoctors());
        subscription.setAmount(input.amount());
        subscription.setAutoRenew(input.autoRenew() == null || input.autoRenew());
        organization.setSubscriptionStatus(status);
        if (input.maxDoctors() != null) organization.setMaxDoctors(input.maxDoctors());
        if (input.trialEndDate() != null) organization.setTrialEndsAt(input.trialEndDate());
        organizations.save(organization);
        return subscriptionView(subscriptions.save(subscription));
    }

    private OrganizationView organizationView(Organization value) {
        return new OrganizationView(value.getId(), value.getOrganizationName(), value.getOrganizationType().name(), value.getOwnerFullName(), value.getOwnerEmail(), value.getOwnerMobile(), Boolean.TRUE.equals(value.getIsActive()), value.getSubscriptionStatus().name(), value.getMaxDoctors(), doctors.countByOrganizationId(value.getId()), value.getTrialEndsAt(), value.getCreatedAt());
    }

    private DoctorView doctorView(Doctor value) {
        boolean enabled = value.getUser() != null ? value.getUser().isActive() : "ACTIVE".equals(value.getStatus());
        return new DoctorView(value.getUser().getId(), value.getOrganization().getId(), value.getOrganization().getOrganizationName(), value.getFullName(), value.getEmail(), value.getPhoneNumber(), value.getSpecialization(), value.getStatus(), enabled);
    }

    private SubscriptionView subscriptionView(Subscription value) {
        return new SubscriptionView(value.getId(), value.getOrganization().getId(), value.getPlanType(), value.getBillingCycle(), value.getStatus(), value.getStartDate(), value.getEndDate(), value.getTrialEndDate(), value.getMaxDoctors(), value.getAmount(), value.getAutoRenew());
    }

    private ResponseStatusException missing() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
    }

    private ResponseStatusException bad(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
