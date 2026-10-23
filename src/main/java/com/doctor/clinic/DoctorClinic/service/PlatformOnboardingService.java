package com.doctor.clinic.DoctorClinic.service;

import com.doctor.clinic.DoctorClinic.request.PlatformOrganizationOnboardingRequest;
import com.doctor.clinic.DoctorClinic.response.PlatformOnboardingResponse;

public interface PlatformOnboardingService {
    PlatformOnboardingResponse onboardOrganization(PlatformOrganizationOnboardingRequest request);
}
