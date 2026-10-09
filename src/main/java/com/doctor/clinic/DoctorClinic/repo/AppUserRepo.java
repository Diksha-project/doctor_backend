package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.AppUser;

@Repository
public interface AppUserRepo extends JpaRepository<AppUser, Long> {
    @EntityGraph(attributePaths = {"organization", "doctor", "userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<AppUser> findByEmailIgnoreCase(String email);

    @Query("""
            select distinct user
            from AppUser user
            join fetch user.organization
            left join fetch user.doctor
            left join fetch user.userRoles userRole
            left join fetch userRole.role role
            left join fetch role.permissions
            where upper(user.email) = upper(:email)
            """)
    Optional<AppUser> findDetailedByEmailIgnoreCase(@Param("email") String email);

    @Query(value = """
            select r.code
            from app_user_roles ur
            join app_roles r on r.id = ur.role_id
            where ur.user_id = :userId and r.active = true
            """, nativeQuery = true)
    Set<String> findActiveRoleCodesByUserId(@Param("userId") Long userId);

    @Query(value = """
            select p.code
            from app_user_roles ur
            join app_roles r on r.id = ur.role_id
            join app_role_permissions rp on rp.role_id = r.id
            join app_permissions p on p.id = rp.permission_id
            where ur.user_id = :userId and r.active = true
            """, nativeQuery = true)
    Set<String> findPermissionCodesByUserId(@Param("userId") Long userId);

    @Query(value = """
            select ur.scope
            from app_user_roles ur
            join app_roles r on r.id = ur.role_id
            where ur.user_id = :userId and r.active = true
            """, nativeQuery = true)
    Set<String> findActiveScopeNamesByUserId(@Param("userId") Long userId);

    @Query(value = """
            select r.id, r.code, r.name, ur.scope
            from app_user_roles ur
            join app_roles r on r.id = ur.role_id
            where ur.user_id = :userId and r.active = true
            order by r.name
            """, nativeQuery = true)
    List<Object[]> findUserRoleDetailsByUserId(@Param("userId") Long userId);

    @EntityGraph(attributePaths = {"organization", "doctor", "userRoles", "userRoles.role", "userRoles.role.permissions"})
    Optional<AppUser> findById(Long id);

    boolean existsByOrganizationIdAndActiveTrueAndUserRolesRoleCode(Long organizationId, String roleCode);

    List<AppUser> findByOrganizationIdOrderByFullNameAsc(Long organizationId);

    Optional<AppUser> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByEmailIgnoreCase(String email);
}
