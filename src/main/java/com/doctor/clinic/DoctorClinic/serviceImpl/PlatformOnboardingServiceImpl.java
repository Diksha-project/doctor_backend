package com.doctor.clinic.DoctorClinic.serviceImpl;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.AppUser;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Role;
import com.doctor.clinic.DoctorClinic.entity.Subscription;
import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.model.OrganizationType;
import com.doctor.clinic.DoctorClinic.model.PlatformOrganizationKind;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.model.SubscriptionStatus;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.repo.RoleRepo;
import com.doctor.clinic.DoctorClinic.repo.SubscriptionRepo;
import com.doctor.clinic.DoctorClinic.request.PlatformOrganizationOnboardingRequest;
import com.doctor.clinic.DoctorClinic.response.PlatformOnboardingResponse;
import com.doctor.clinic.DoctorClinic.service.PlatformOnboardingService;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PlatformOnboardingServiceImpl implements PlatformOnboardingService {
    private static final int DEFAULT_TRIAL_DAYS = 30;

    private final OrganizationRepo organizations;
    private final AppUserRepo users;
    private final RoleRepo roles;
    private final SubscriptionRepo subscriptions;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public PlatformOnboardingResponse onboardOrganization(PlatformOrganizationOnboardingRequest request) {
        String email = request.getSuperAdminEmail().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(request.getTemporaryPassword());
        int maxDoctors = resolveMaxDoctors(request);
        LocalDate trialEnd = LocalDate.now().plusDays(
                request.getTrialDays() == null ? DEFAULT_TRIAL_DAYS : request.getTrialDays());

        Role superAdminRole = roles.findWithPermissionsByCodeAndOrganizationIsNull("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role is missing"));

        try {
            Organization organization = organizations.saveAndFlush(Organization.builder()
                    .ownerFullName(request.getSuperAdminName().trim())
                    .ownerEmail(email)
                    .ownerMobile(request.getSuperAdminMobile().trim())
                    .organizationName(request.getOrganizationName().trim())
                    .organizationType(resolveOrganizationType(request.getOrganizationKind()))
                    .passwordHash(passwordHash)
                    .maxDoctors(maxDoctors)
                    .doctorsCount(0)
                    .subscriptionStatus(SubscriptionStatus.TRIAL)
                    .trialEndsAt(trialEnd)
                    .isActive(true)
                    .build());

            AppUser superAdmin = users.save(createSuperAdmin(
                    organization, request.getSuperAdminName(), email, passwordHash, superAdminRole));
            subscriptions.save(createTrial(organization, maxDoctors, trialEnd));

            return PlatformOnboardingResponse.builder()
                    .organizationId(organization.getId())
                    .userId(superAdmin.getId())
                    .accountType(request.getOrganizationKind().name())
                    .organizationName(organization.getOrganizationName())
                    .loginEmail(superAdmin.getEmail())
                    .subscriptionStatus(organization.getSubscriptionStatus().name())
                    .message("Organization and Super Admin account created successfully.")
                    .build();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(409, "ACCOUNT_ALREADY_EXISTS",
                    "An account with this email or mobile number already exists");
        }
    }

    private AppUser createSuperAdmin(Organization organization, String name, String email,
                                     String passwordHash, Role role) {
        AppUser user = new AppUser();
        user.setOrganization(organization);
        user.setFullName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setActive(true);

        UserRole assignment = new UserRole();
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setScope(ResourceScope.ORGANIZATION);
        user.getUserRoles().add(assignment);
        return user;
    }

    private Subscription createTrial(Organization organization, int maxDoctors, LocalDate trialEnd) {
        Subscription subscription = new Subscription();
        subscription.setOrganization(organization);
        subscription.setPlanType("BASIC");
        subscription.setBillingCycle("MONTHLY");
        subscription.setStartDate(LocalDate.now());
        subscription.setTrialEndDate(trialEnd);
        subscription.setStatus("TRIAL");
        subscription.setAmount(BigDecimal.ZERO);
        subscription.setMaxDoctors(maxDoctors);
        subscription.setAutoRenew(false);
        return subscription;
    }

    private int resolveMaxDoctors(PlatformOrganizationOnboardingRequest request) {
        if (request.getOrganizationKind() == PlatformOrganizationKind.SINGLE_DOCTOR_OUTLET) {
            return 1;
        }
        if (request.getMaxDoctors() != null) {
            return request.getMaxDoctors();
        }
        return resolveOrganizationType(request.getOrganizationKind()).getDefaultMaxDoctors();
    }

    private OrganizationType resolveOrganizationType(PlatformOrganizationKind kind) {
        return switch (kind) {
            case HOSPITAL -> OrganizationType.HOSPITAL;
            case CLINIC, SINGLE_DOCTOR_OUTLET -> OrganizationType.CLINIC;
        };
    }
}
