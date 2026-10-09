package com.doctor.clinic.DoctorClinic.repo;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.doctor.clinic.DoctorClinic.entity.Permission;

@Repository
public interface PermissionRepo extends JpaRepository<Permission, Long> {
    List<Permission> findByCodeIn(Collection<String> codes);
}
