package com.doctor.clinic.DoctorClinic.serviceImpl;

import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.entity.Permission;
import com.doctor.clinic.DoctorClinic.entity.Role;
import com.doctor.clinic.DoctorClinic.repo.PermissionRepo;
import com.doctor.clinic.DoctorClinic.repo.RoleRepo;
import com.doctor.clinic.DoctorClinic.repo.UserRoleRepo;
import com.doctor.clinic.DoctorClinic.request.RoleUpsertRequest;
import com.doctor.clinic.DoctorClinic.security.PermissionCode;
import com.doctor.clinic.DoctorClinic.service.RbacRoleService;
import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class RbacRoleServiceImpl implements RbacRoleService {
    private final RoleRepo roles;
    private final PermissionRepo permissions;
    private final UserRoleRepo userRoles;

    @Override
    public List<Role> listRoles(Long organizationId) {
        Map<String, Role> rolesByCode = new LinkedHashMap<>();
        roles.findByOrganizationIdOrOrganizationIsNullOrderBySystemRoleDescNameAsc(organizationId).stream()
                .filter(role -> !"PLATFORM_ADMIN".equals(role.getCode()))
                .forEach(role -> rolesByCode.merge(role.getCode(), role,
                        (existing, candidate) -> candidate.getOrganization() != null ? candidate : existing));
        return List.copyOf(rolesByCode.values());
    }

    @Override
    @Transactional
    public Role createRole(Long organizationId, RoleUpsertRequest request) {
        String code = normalizeCode(request.getCode() == null ? request.getName() : request.getCode());
        if (roles.existsByOrganizationIdAndCodeIgnoreCase(organizationId, code)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Role code already exists");
        }
        Role role = new Role();
        role.setOrganization(organizationReference(organizationId));
        role.setCode(code);
        role.setSystemRole(false);
        applyChanges(role, request);
        return roles.save(role);
    }

    @Override
    @Transactional
    public Role updateRole(Long organizationId, Long roleId, RoleUpsertRequest request) {
        Role source = roles.findWithPermissionsById(roleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Role not found"));
        if ("PLATFORM_ADMIN".equals(source.getCode())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Platform Admin role cannot be changed here");
        }
        Role role = source;
        if (source.getOrganization() == null) {
            role = roles.findByOrganizationIdAndCodeIgnoreCase(organizationId, source.getCode())
                    .orElseGet(() -> copyForOrganization(source, organizationId));
        } else if (!organizationId.equals(source.getOrganization().getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Role belongs to another organization");
        }
        applyChanges(role, request);
        role = roles.saveAndFlush(role);
        if (source.getOrganization() == null) {
            userRoles.copyOrganizationAssignments(organizationId, source.getId(), role.getId());
            userRoles.deleteOrganizationAssignments(organizationId, source.getId());
        }
        return role;
    }

    private void applyChanges(Role role, RoleUpsertRequest request) {
        role.setName(requiredText(request.getName(), "Role name is required"));
        role.setDescription(request.getDescription());
        role.setActive(request.getActive() == null || request.getActive());
        role.setPermissions(resolvePermissions(request.getPermissions()));
    }

    private Set<Permission> resolvePermissions(List<String> permissionCodes) {
        if (permissionCodes == null || permissionCodes.isEmpty()) return Set.of();
        Set<String> codes = permissionCodes.stream()
                .filter(code -> code != null && !code.isBlank())
                .map(String::trim)
                .distinct()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        if (codes.stream().anyMatch(code -> PermissionCode.fromCode(code).isEmpty())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown canonical permission code");
        }
        Set<Permission> resolved = new HashSet<>(permissions.findByCodeIn(codes));
        if (resolved.size() != codes.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Canonical permission is not available");
        }
        return resolved;
    }

    private Role copyForOrganization(Role source, Long organizationId) {
        Role role = new Role();
        role.setOrganization(organizationReference(organizationId));
        role.setCode(source.getCode());
        role.setName(source.getName());
        role.setDescription(source.getDescription());
        role.setSystemRole(false);
        role.setActive(source.isActive());
        role.setPermissions(new HashSet<>(source.getPermissions()));
        return role;
    }

    private Organization organizationReference(Long id) {
        Organization organization = new Organization();
        organization.setId(id);
        return organization;
    }

    private String normalizeCode(String value) {
        return requiredText(value, "Role code is required").toUpperCase().replaceAll("[^A-Z0-9]+", "_");
    }

    private String requiredText(String value, String message) {
        if (value == null || value.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        return value.trim();
    }
}
