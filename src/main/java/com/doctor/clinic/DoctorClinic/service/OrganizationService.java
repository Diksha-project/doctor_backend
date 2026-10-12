package com.doctor.clinic.DoctorClinic.service;


import org.springframework.http.ResponseEntity;

import com.doctor.clinic.DoctorClinic.request.OrganizationRegistrationRequest;
import com.doctor.clinic.DoctorClinic.response.OrganizationDashboardResponse;
import com.doctor.clinic.DoctorClinic.request.UpdateAutomationSettingsRequest;
import java.util.Map;

public interface OrganizationService {

	String registerOrganization(OrganizationRegistrationRequest req);

	OrganizationDashboardResponse  getOrganizationDashboard(Long organizationId);
	Map<String, Integer> getAutomationSettings(Long organizationId);
	Map<String, Integer> updateAutomationSettings(Long organizationId, UpdateAutomationSettingsRequest request);
}
