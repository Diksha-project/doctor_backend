package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.UserRole;
import com.doctor.clinic.DoctorClinic.entity.UserRoleId;

@Repository
public interface UserRoleRepo extends JpaRepository<UserRole, UserRoleId> {
}
