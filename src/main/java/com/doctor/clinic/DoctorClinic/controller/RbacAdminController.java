package com.doctor.clinic.DoctorClinic.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

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

import com.doctor.clinic.DoctorClinic.entity.AppUser;
import com.doctor.clinic.DoctorClinic.entity.Doctor;
import com.doctor.clinic.DoctorClinic.entity.Permission;
import com.doctor.clinic.DoctorClinic.entity.Role;
import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.repo.AppUserRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PermissionRepo;
import com.doctor.clinic.DoctorClinic.repo.RoleRepo;
import com.doctor.clinic.DoctorClinic.request.RoleUpsertRequest;
import com.doctor.clinic.DoctorClinic.request.UserUpsertRequest;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.security.CurrentUser;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;

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
    private final PermissionRepo permissionRepo;
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
        return roleRepo.findByOrganizationIdOrOrganizationIsNullOrderBySystemRoleDescNameAsc(currentUser.organizationId())
                .stream().map(this::roleResponse).toList();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createRole(@RequestBody RoleUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_EDIT);
        String code = normalizeCode(request.getCode() == null ? request.getName() : request.getCode());
        if (roleRepo.existsByOrganizationIdAndCodeIgnoreCase(currentUser.organizationId(), code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
        }
        Role role = new Role();
        role.setCode(code);
        role.setName(requiredText(request.getName(), "Role name is required"));
        role.setDescription(request.getDescription());
        role.setOrganization(new com.doctor.clinic.DoctorClinic.entity.Organization());
        role.getOrganization().setId(currentUser.organizationId());
        role.setSystemRole(false);
        role.setActive(true);
        role.setPermissions(resolvePermissions(request.getPermissions()));
        return roleResponse(roleRepo.save(role));
    }

    @PutMapping("/roles/{roleId}")
    public Map<String, Object> updateRole(@PathVariable Long roleId, @RequestBody RoleUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_EDIT);
        Role role = roleRepo.findWithPermissionsById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if (role.getOrganization() == null || !currentUser.organizationId().equals(role.getOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "System roles cannot be edited here");
        }
        role.setName(requiredText(request.getName(), "Role name is required"));
        role.setDescription(request.getDescription());
        role.setActive(request.getActive() == null || request.getActive());
        role.setPermissions(resolvePermissions(request.getPermissions()));
        return roleResponse(roleRepo.save(role));
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users() {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_VIEW);
        return appUserRepo.findByOrganizationIdOrderByFullNameAsc(currentUser.organizationId())
                .stream().map(this::userResponse).toList();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> createUser(@RequestBody UserUpsertRequest request) {
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
        assignDoctor(user, request.getDoctorId(), currentUser.organizationId());
        assignRoles(user, request.getRoleIds(), currentUser.organizationId());
        return userResponse(appUserRepo.save(user));
    }

    @PutMapping("/users/{userId}")
    public Map<String, Object> updateUser(@PathVariable Long userId, @RequestBody UserUpsertRequest request) {
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_EDIT);
        AppUser user = appUserRepo.findByIdAndOrganizationId(userId, currentUser.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        user.setFullName(requiredText(request.getFullName(), "Full name is required"));
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }
        assignDoctor(user, request.getDoctorId(), currentUser.organizationId());
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
        return userResponse(appUserRepo.save(user));
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

    private void assignDoctor(AppUser user, Long doctorId, Long organizationId) {
        if (doctorId == null) {
            user.setDoctor(null);
            return;
        }
        Doctor doctor = doctorRepo.findByIdAndOrganizationId(doctorId, organizationId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Doctor not found"));
        user.setDoctor(doctor);
    }

    private void assignRoles(AppUser user, List<Long> roleIds, Long organizationId) {
        user.getUserRoles().clear();
        if (roleIds == null || roleIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role is required");
        }
        for (Long roleId : roleIds) {
            Role role = roleRepo.findWithPermissionsById(roleId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
            if (role.getOrganization() != null && !organizationId.equals(role.getOrganization().getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role belongs to another organization");
            }
            UserRole userRole = new UserRole();
            userRole.setUser(user);
            userRole.setRole(role);
            userRole.setScope(user.getDoctor() == null ? ResourceScope.ORGANIZATION : ResourceScope.OWN_DOCTOR);
            user.getUserRoles().add(userRole);
        }
    }

    private Set<Permission> resolvePermissions(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) {
            return Set.of();
        }
        Set<String> canonicalCodes = permissionCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        if (canonicalCodes.stream().anyMatch(code -> PermissionCode.fromCode(code).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown canonical permission code");
        }
        Set<Permission> permissions = new java.util.HashSet<>(permissionRepo.findByCodeIn(canonicalCodes));
        if (permissions.size() != canonicalCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canonical permission is not available");
        }
        return permissions;
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
                "doctorId", user.getDoctor() == null ? "" : user.getDoctor().getId(),
                "roles", roles);
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

    private String normalizeCode(String value) {
        return requiredText(value, "Role code is required").trim().toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

}
