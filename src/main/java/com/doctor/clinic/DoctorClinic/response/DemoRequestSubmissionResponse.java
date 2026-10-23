package com.doctor.clinic.DoctorClinic.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DemoRequestSubmissionResponse {
    Long id;
    String status;
    String message;
}
