package com.doctor.clinic.DoctorClinic.request;

import java.util.List;
import java.math.BigDecimal;

import lombok.Data;

@Data
public class UserUpsertRequest {
    private String fullName;
    private String email;
    private String password;
    private Boolean active;
    private List<Long> roleIds;
    private String registrationNumber;
    private String qualification;
    private String specialization;
    private Integer experienceYears;
    private BigDecimal consultationFee;
    private String phoneNumber;
}
