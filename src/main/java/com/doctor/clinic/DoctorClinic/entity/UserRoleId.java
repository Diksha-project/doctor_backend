package com.doctor.clinic.DoctorClinic.entity;

import java.io.Serializable;

import lombok.Data;

@Data
public class UserRoleId implements Serializable {
    private Long user;
    private Long role;
}
