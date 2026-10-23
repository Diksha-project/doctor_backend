package com.doctor.clinic.DoctorClinic.response;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PlatformOnboardingResponse {
    Long organizationId;
    Long userId;
    String accountType;
    String organizationName;
    String loginEmail;
    String subscriptionStatus;
    String message;
}
