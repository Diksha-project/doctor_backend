package com.doctor.clinic.DoctorClinic.request;

import java.util.List;

import lombok.Data;

@Data
public class UserUpsertRequest {
    private String fullName;
    private String email;
    private String password;
    private Long doctorId;
    private Boolean active;
    private List<Long> roleIds;
}
