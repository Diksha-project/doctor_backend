package com.doctor.clinic.DoctorClinic.request;

import java.util.List;

import lombok.Data;

@Data
public class RoleUpsertRequest {
    private String name;
    private String code;
    private String description;
    private Boolean active;
    private List<String> permissions;
}
