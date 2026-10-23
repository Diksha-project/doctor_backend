package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.entity.UserRoleId;

@Repository
public interface UserRoleRepo extends JpaRepository<UserRole, UserRoleId> {
    @Modifying
    @Query("delete from UserRole userRole where userRole.user.id = :userId")
    void deleteAllByUserId(@Param("userId") Long userId);

    @Modifying
    @Query(value = """
            insert into app_user_roles (user_id, role_id, scope)
            select assignment.user_id, :newRoleId, assignment.scope
            from app_user_roles assignment
            join app_users app_user on app_user.id = assignment.user_id
            where assignment.role_id = :oldRoleId
              and app_user.organization_id = :organizationId
            on conflict do nothing
            """, nativeQuery = true)
    void copyOrganizationAssignments(@Param("organizationId") Long organizationId,
                                     @Param("oldRoleId") Long oldRoleId,
                                     @Param("newRoleId") Long newRoleId);

    @Modifying
    @Query(value = """
            delete from app_user_roles assignment
            using app_users app_user
            where assignment.user_id = app_user.id
              and assignment.role_id = :oldRoleId
              and app_user.organization_id = :organizationId
            """, nativeQuery = true)
    void deleteOrganizationAssignments(@Param("organizationId") Long organizationId,
                                       @Param("oldRoleId") Long oldRoleId);
}
