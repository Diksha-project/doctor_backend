package com.doctor.clinic.DoctorClinic.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.math.BigDecimal;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.transaction.annotation.Transactional;
import jakarta.validation.Valid;

import com.doctor.clinic.DoctorClinic.entity.AppUser;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Permission;
import com.doctor.clinic.DoctorClinic.entity.Role;
import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.RoleRepo;
import com.doctor.clinic.DoctorClinic.request.RoleUpsertRequest;
import com.doctor.clinic.DoctorClinic.request.UserUpsertRequest;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.security.CurrentUser;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;
import com.doctor.clinic.DoctorClinic.service.RbacRoleService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class RbacAdminController {
    // PermissionCode is the single catalogue. Legacy rows remain only for recovery.
    private static final List<PermissionCode> PERMISSION_OPTIONS = List.of(PermissionCode.values());
    private final AuthorizationService authorizationService;
    private final AppUserRepo appUserRepo;
    private final RoleRepo roleRepo;
    private final RbacRoleService rbacRoleService;
    private final DoctorRepo doctorRepo;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/permissions")
    public List<Map<String, Object>> permissions() {
        authorizationService.requirePermission(PermissionCode.ROLES_VIEW);
        return PERMISSION_OPTIONS.stream()
                .map(option -> Map.<String, Object>of(
                        "code", option.code(),
                        "module", option.module(),
                        "action", option.action(),
                        "description", option.description()))
                .toList();
    }

    @GetMapping("/roles")
    public List<Map<String, Object>> roles() {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_VIEW);
        return rbacRoleService.listRoles(currentUser.organizationId()).stream().map(this::roleResponse).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRole(@RequestBody RoleUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_EDIT);
        return roleResponse(rbacRoleService.createRole(currentUser.organizationId(), request));
    }

    @PutMapping("/roles/{roleId}")
    public Map<String, Object> updateRole(@PathVariable Long roleId, @RequestBody RoleUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_EDIT);
        return roleResponse(rbacRoleService.updateRole(currentUser.organizationId(), roleId, request));
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_VIEW);
        return appUserRepo.findByOrganizationIdOrderByFullNameAsc(currentUser.organizationId())
                .stream().map(this::userResponse).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    @Transactional
    public Map<String, Object> createUser(@Valid @RequestBody UserUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_EDIT);
        if (request.getEmail() == null || request.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email and password are required");
        }
        if (appUserRepo.existsByEmailIgnoreCase(request.getEmail())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }
        AppUser user = new AppUser();
        user.setOrganization(new com.doctor.clinic.DoctorClinic.entity.Organization());
        user.getOrganization().setId(currentUser.organizationId());
        user.setFullName(requiredText(request.getFullName(), "Full name is required"));
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setActive(request.getActive() == null || request.getActive());
        assignRoles(user, request.getRoleIds(), currentUser.organizationId());
        AppUser savedUser = appUserRepo.save(user);
        syncDoctorProfile(savedUser, request, currentUser.organizationId());
        return userResponse(savedUser);
    }

    @PutMapping("/users/{userId}")
    @Transactional
    public Map<String, Object> updateUser(@PathVariable Long userId,
            @Valid @RequestBody UserUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_EDIT);
        AppUser user = appUserRepo.findByIdAndOrganizationId(userId, currentUser.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setFullName(requiredText(request.getFullName(), "Full name is required"));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        assignRoles(user, request.getRoleIds(), currentUser.organizationId());
        if (request.getActive() != null && !request.getActive() && hasRole(user, "SUPER_ADMIN")) {
            long activeSuperAdmins = appUserRepo.findByOrganizationIdOrderByFullNameAsc(currentUser.organizationId()).stream()
                    .filter(AppUser::isActive)
                    .filter(candidate -> !candidate.getId().equals(userId))
                    .filter(candidate -> hasRole(candidate, "SUPER_ADMIN"))
                    .count();
            if (activeSuperAdmins == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate the final Super Admin");
            }
        }
        user.setActive(request.getActive() == null || request.getActive());
        AppUser savedUser = appUserRepo.save(user);
        syncDoctorProfile(savedUser, request, currentUser.organizationId());
        return userResponse(savedUser);
    }

    @PatchMapping("/users/{userId}/active")
    public Map<String, Object> setUserActive(@PathVariable Long userId, @RequestBody Map<String, Boolean> request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_EDIT);
        AppUser user = appUserRepo.findByIdAndOrganizationId(userId, currentUser.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        boolean active = Boolean.TRUE.equals(request.get("active"));
        if (!active && hasRole(user, "SUPER_ADMIN")) {
            long activeSuperAdmins = appUserRepo.findByOrganizationIdOrderByFullNameAsc(currentUser.organizationId()).stream()
                    .filter(AppUser::isActive)
                    .filter(candidate -> !candidate.getId().equals(userId))
                    .filter(candidate -> hasRole(candidate, "SUPER_ADMIN"))
                    .count();
            if (activeSuperAdmins == 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot deactivate the final Super Admin");
            }
        }
        user.setActive(active);
        return userResponse(appUserRepo.save(user));
    }

    private void assignRoles(AppUser user, List<Long> roleIds, Long organizationId) {
        if (roleIds == null || roleIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role is required");
        }
        List<Long> distinctRoleIds = roleIds.stream().distinct().toList();
        List<Role> selectedRoles = roleRepo.findAllWithPermissionsByIdIn(distinctRoleIds);
        if (selectedRoles.size() != distinctRoleIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found");
        }
        user.getUserRoles().removeIf(userRole -> !distinctRoleIds.contains(userRole.getRole().getId()));
        Map<Long, UserRole> existingAssignments = user.getUserRoles().stream()
                .collect(java.util.stream.Collectors.toMap(userRole -> userRole.getRole().getId(), userRole -> userRole));
        for (Role role : selectedRoles) {
            if ("PLATFORM_ADMIN".equals(role.getCode())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "Platform administrator roles are managed outside organization RBAC");
            }
            if (role.getOrganization() != null && !organizationId.equals(role.getOrganization().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role belongs to another organization");
            }
            UserRole userRole = existingAssignments.get(role.getId());
            if (userRole == null) {
                userRole = new UserRole();
                userRole.setUser(user);
                userRole.setRole(role);
                user.getUserRoles().add(userRole);
            }
            userRole.setScope("DOCTOR".equals(role.getCode())
                    ? ResourceScope.OWN_DOCTOR : ResourceScope.ORGANIZATION);
        }
    }

    private Map<String, Object> roleResponse(Role role) {
        Set<String> rawCodes = role.getPermissions().stream().map(Permission::getCode)
                .filter(code -> PermissionCode.fromCode(code).isPresent())
                .collect(java.util.stream.Collectors.toSet());
        return Map.of(
                "id", role.getId(),
                "code", role.getCode(),
                "name", role.getName(),
                "description", role.getDescription() == null ? "" : role.getDescription(),
                "systemRole", role.isSystemRole(),
                "active", role.isActive(),
                "permissions", PERMISSION_OPTIONS.stream()
                        .map(PermissionCode::code)
                        .filter(rawCodes::contains)
                        .toList());
    }

    private Map<String, Object> userResponse(AppUser user) {
        List<Map<String, Object>> roles = appUserRepo.findUserRoleDetailsByUserId(user.getId()).stream()
                .map(row -> Map.<String, Object>of(
                        "id", ((Number) row[0]).longValue(),
                        "code", row[1],
                        "name", row[2],
                        "scope", row[3]))
                .toList();
        return Map.of(
                "id", user.getId(),
                "fullName", user.getFullName(),
                "email", user.getEmail(),
                "active", user.isActive(),
                "organizationId", user.getOrganization().getId(),
                "doctorProfile", doctorRepo.findByUserIdAndOrganizationId(user.getId(), user.getOrganization().getId()).isPresent(),
                "roles", roles);
    }

    private void syncDoctorProfile(AppUser user, UserUpsertRequest request, Long organizationId) {
        boolean doctorRoleSelected = user.getUserRoles().stream()
                .anyMatch(userRole -> userRole.getRole().isActive()
                        && "DOCTOR".equals(userRole.getRole().getCode()));
        Doctor existing = doctorRepo.findByUserIdAndOrganizationId(user.getId(), organizationId).orElse(null);
        if (!doctorRoleSelected) {
            if (existing != null && !"INACTIVE".equals(existing.getStatus())) {
                existing.setStatus("INACTIVE");
                doctorRepo.save(existing);
            }
            return;
        }
        if (existing == null && (request.getRegistrationNumber() == null || request.getRegistrationNumber().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Registration number is required when assigning the Doctor role");
        }

        Doctor doctor = existing == null ? new Doctor() : existing;
		String registrationNumber = valueOrExisting(request.getRegistrationNumber(), doctor.getRegistrationNumber());
		String phoneNumber = valueOrExisting(request.getPhoneNumber(), doctor.getPhoneNumber());
		if (doctorRepo.existsByRegistrationNumberAndUserIdNot(registrationNumber, user.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Registration number is already used by another doctor");
		}
		if (doctorRepo.existsByPhoneNumberAndUserIdNot(phoneNumber, user.getId())) {
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					"Mobile number is already used by another doctor");
		}
        doctor.setUser(user);
        doctor.setOrganization(user.getOrganization());
        String[] name = user.getFullName().trim().split("\\s+", 2);
        doctor.setFirstName(name[0]);
        doctor.setLastName(name.length > 1 ? name[1] : null);
        doctor.setEmail(user.getEmail());
        doctor.setRegistrationNumber(registrationNumber);
        doctor.setPhoneNumber(phoneNumber);
        if (doctor.getPhoneNumber() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Mobile number is required when assigning the Doctor role");
        }
        doctor.setQualification(valueOrExisting(request.getQualification(), doctor.getQualification()));
        doctor.setSpecialization(valueOrExisting(request.getSpecialization(), doctor.getSpecialization()));
        doctor.setExperienceYears(request.getExperienceYears() == null
                ? doctor.getExperienceYears() : request.getExperienceYears());
        doctor.setConsultationFee(request.getConsultationFee() == null
                ? (doctor.getConsultationFee() == null ? BigDecimal.ZERO : doctor.getConsultationFee())
                : request.getConsultationFee());
        doctor.setStatus("ACTIVE");
        doctorRepo.save(doctor);
    }

    private String valueOrExisting(String value, String existing) {
        return value == null || value.isBlank() ? existing : value.trim();
    }

    private boolean hasRole(AppUser user, String roleCode) {
        return appUserRepo.findActiveRoleCodesByUserId(user.getId()).contains(roleCode);
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
        return value.trim();
    }

}
