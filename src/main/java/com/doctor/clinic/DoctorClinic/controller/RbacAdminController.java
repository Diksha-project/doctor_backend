package com.doctor.clinic.DoctorClinic.controller;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final AuthorizationService authorizationService;
    private final AppUserRepo appUserRepo;
    private final RoleRepo roleRepo;
    private final PermissionRepo permissionRepo;
    private final DoctorRepo doctorRepo;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/permissions")
    public List<Map<String, Object>> permissions() {
        authorizationService.requirePermission(PermissionCode.ROLES_VIEW);
        return permissionRepo.findAll().stream()
                .sorted(Comparator.comparing(Permission::getModule).thenComparing(Permission::getCode))
                .map(permission -> Map.<String, Object>of(
                        "code", permission.getCode(),
                        "module", permission.getModule(),
                        "action", permission.getAction(),
                        "description", permission.getDescription() == null ? "" : permission.getDescription()))
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
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_MANAGE);
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
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.ROLES_MANAGE);
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
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_MANAGE);
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
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_MANAGE);
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
        CurrentUser currentUser = authorizationService.requirePermission(PermissionCode.USERS_MANAGE);
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
        Set<String> requestedCodes = permissionCodes.stream()
                .map(String::trim)
                .filter(code -> !code.isBlank())
                .collect(Collectors.toSet());
        Set<Permission> permissions = permissionRepo.findByCodeIn(requestedCodes).stream().collect(Collectors.toSet());
        if (permissions.size() != requestedCodes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown permission code");
        }
        return permissions;
    }

    private Map<String, Object> roleResponse(Role role) {
        return Map.of(
                "id", role.getId(),
                "code", role.getCode(),
                "name", role.getName(),
                "description", role.getDescription() == null ? "" : role.getDescription(),
                "systemRole", role.isSystemRole(),
                "active", role.isActive(),
                "permissions", role.getPermissions().stream().map(Permission::getCode).sorted().toList());
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
