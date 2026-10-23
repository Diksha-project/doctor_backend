package com.doctor.clinic.DoctorClinic.response;

import java.time.Instant;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DemoRequestResponse {
    Long id;
    String organizationName;
    String contactName;
    String email;
    String phone;
    String city;
    Integer expectedDoctors;
    String message;
    String status;
    String internalNotes;
    Instant requestedAt;
    Instant updatedAt;
}
