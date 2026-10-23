package com.doctor.clinic.DoctorClinic.service;

import com.doctor.clinic.DoctorClinic.entity.Role;
import com.doctor.clinic.DoctorClinic.request.RoleUpsertRequest;
import java.util.List;

public interface RbacRoleService {
    List<Role> listRoles(Long organizationId);
    Role createRole(Long organizationId, RoleUpsertRequest request);
    Role updateRole(Long organizationId, Long roleId, RoleUpsertRequest request);
}
