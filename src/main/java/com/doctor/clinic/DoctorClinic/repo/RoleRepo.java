package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.Role;

@Repository
public interface RoleRepo extends JpaRepository<Role, Long> {
    @EntityGraph(attributePaths = {"permissions", "organization"})
    Optional<Role> findWithPermissionsById(Long id);

    @EntityGraph(attributePaths = {"permissions", "organization"})
    Optional<Role> findWithPermissionsByCodeAndOrganizationIsNull(String code);

    List<Role> findByOrganizationIdOrOrganizationIsNullOrderBySystemRoleDescNameAsc(Long organizationId);

    Optional<Role> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndCodeIgnoreCase(Long organizationId, String code);
}
