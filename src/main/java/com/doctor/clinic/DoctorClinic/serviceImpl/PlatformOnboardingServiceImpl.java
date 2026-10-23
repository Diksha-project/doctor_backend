package com.doctor.clinic.DoctorClinic.serviceImpl;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.entity.AppUser;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Role;
import com.doctor.clinic.DoctorClinic.entity.Subscription;
import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.model.OrganizationType;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.model.SubscriptionStatus;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
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
    private final DoctorRepo doctors;
    private final RoleRepo roles;
    private final SubscriptionRepo subscriptions;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public PlatformOnboardingResponse onboardOrganization(PlatformOrganizationOnboardingRequest request) {
        String email = request.getSuperAdminEmail().trim().toLowerCase(Locale.ROOT);
        String passwordHash = passwordEncoder.encode(request.getTemporaryPassword());
        int maxDoctors = request.getMaxDoctors() == null
                ? OrganizationType.HOSPITAL.getDefaultMaxDoctors() : request.getMaxDoctors();
        LocalDate trialEnd = LocalDate.now().plusDays(
                request.getTrialDays() == null ? DEFAULT_TRIAL_DAYS : request.getTrialDays());

        Role superAdminRole = roles.findWithPermissionsByCodeAndOrganizationIsNull("SUPER_ADMIN")
                .orElseThrow(() -> new IllegalStateException("SUPER_ADMIN role is missing"));
        Role doctorRole = Boolean.TRUE.equals(request.getSuperAdminIsDoctor())
                ? roles.findWithPermissionsByCodeAndOrganizationIsNull("DOCTOR")
                        .orElseThrow(() -> new IllegalStateException("DOCTOR role is missing"))
                : null;

        try {
            Organization organization = organizations.saveAndFlush(Organization.builder()
                    .ownerFullName(request.getSuperAdminName().trim())
                    .ownerEmail(email)
                    .ownerMobile(request.getSuperAdminMobile().trim())
                    .organizationName(request.getOrganizationName().trim())
                    .organizationType(OrganizationType.HOSPITAL)
                    .passwordHash(passwordHash)
                    .maxDoctors(maxDoctors)
                    .doctorsCount(Boolean.TRUE.equals(request.getSuperAdminIsDoctor()) ? 1 : 0)
                    .subscriptionStatus(SubscriptionStatus.TRIAL)
                    .trialEndsAt(trialEnd)
                    .isActive(true)
                    .build());

            AppUser superAdmin = users.save(createSuperAdmin(organization,
                    request.getSuperAdminName(), email, passwordHash, superAdminRole, doctorRole));
            if (doctorRole != null) {
                doctors.save(createDoctor(superAdmin, organization, request, email));
            }
            subscriptions.save(createTrial(organization, maxDoctors, trialEnd));
            organizations.flush();

            return PlatformOnboardingResponse.builder()
                    .organizationId(organization.getId())
                    .userId(superAdmin.getId())
                    .accountType(Boolean.TRUE.equals(request.getSuperAdminIsDoctor())
                            ? "ORGANIZATION_WITH_DOCTOR_ADMIN" : "ORGANIZATION")
                    .organizationName(organization.getOrganizationName())
                    .loginEmail(superAdmin.getEmail())
                    .subscriptionStatus(organization.getSubscriptionStatus().name())
                    .message("Organization and Super Admin account created successfully.")
                    .build();
        } catch (DataIntegrityViolationException exception) {
            throw duplicateFieldException(exception, email);
        }
    }

    private AppUser createSuperAdmin(Organization organization, String name, String email,
                                     String passwordHash, Role superAdminRole, Role doctorRole) {
        AppUser user = new AppUser();
        user.setOrganization(organization);
        user.setFullName(name.trim());
        user.setEmail(email);
        user.setPasswordHash(passwordHash);
        user.setActive(true);

        user.getUserRoles().add(roleAssignment(user, superAdminRole, ResourceScope.ORGANIZATION));
        if (doctorRole != null) {
            user.getUserRoles().add(roleAssignment(user, doctorRole, ResourceScope.OWN_DOCTOR));
        }
        return user;
    }

    private UserRole roleAssignment(AppUser user, Role role, ResourceScope scope) {
        UserRole assignment = new UserRole();
        assignment.setUser(user);
        assignment.setRole(role);
        assignment.setScope(scope);
        return assignment;
    }

    private Doctor createDoctor(AppUser user, Organization organization, PlatformOrganizationOnboardingRequest request,
                                String email) {
        String fullName = request.getSuperAdminName().trim();
        int separator = fullName.indexOf(' ');
        return Doctor.builder()
                .user(user)
                .firstName(separator < 0 ? fullName : fullName.substring(0, separator))
                .lastName(separator < 0 ? null : fullName.substring(separator + 1).trim())
                .registrationNumber(request.getDoctorRegistrationNumber().trim())
                .qualification(clean(request.getDoctorQualification()))
                .specialization(clean(request.getDoctorSpecialization()))
                .experienceYears(request.getDoctorExperienceYears())
                .consultationFee(request.getDoctorConsultationFee() == null
                        ? BigDecimal.ZERO : request.getDoctorConsultationFee())
                .phoneNumber(request.getSuperAdminMobile().trim())
                .email(email)
                .organization(organization)
                .status("ACTIVE")
                .build();
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

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BusinessException duplicateFieldException(DataIntegrityViolationException exception, String email) {
        String databaseMessage = exception.getMostSpecificCause().getMessage().toLowerCase(Locale.ROOT);
        if (!databaseMessage.contains("duplicate key") && !databaseMessage.contains("unique constraint")) {
            return new BusinessException(409, "ONBOARDING_DATA_CONFLICT",
                    "The organization could not be created because one of its values conflicts with database rules");
        }
        if (databaseMessage.contains("registration_number")) {
            return new BusinessException(409, "DUPLICATE_REGISTRATION_NUMBER",
                    "This doctor registration number is already in use");
        }
        if (databaseMessage.contains("owner_mobile") || databaseMessage.contains("doctor_mobile")) {
            return new BusinessException(409, "DUPLICATE_MOBILE",
                    "This mobile number is already in use");
        }
        if (databaseMessage.contains("email")) {
            return new BusinessException(409, "DUPLICATE_EMAIL",
                    "This email address is already in use: " + email);
        }
        return new BusinessException(409, "DUPLICATE_ACCOUNT_DATA",
                "One of the supplied account details is already in use");
    }

}
