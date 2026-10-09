package com.doctor.clinic.DoctorClinic.entity;

import com.doctor.clinic.DoctorClinic.model.ResourceScope;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Entity
@Table(name = "app_user_roles")
@IdClass(UserRoleId.class)
@Data
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class UserRole {
    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    @EqualsAndHashCode.Include
    private AppUser user;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id")
    @EqualsAndHashCode.Include
    private Role role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResourceScope scope = ResourceScope.ORGANIZATION;
}
