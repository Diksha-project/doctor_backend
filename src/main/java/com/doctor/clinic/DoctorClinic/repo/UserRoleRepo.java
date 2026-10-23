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
}
