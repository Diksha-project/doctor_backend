package com.doctor.clinic.DoctorClinic.service;


import org.springframework.http.ResponseEntity;

import com.doctor.clinic.DoctorClinic.request.OrganizationRegistrationRequest;
import com.doctor.clinic.DoctorClinic.response.OrganizationDashboardResponse;

public interface OrganizationService {

	String registerOrganization(OrganizationRegistrationRequest req);

	OrganizationDashboardResponse  getOrganizationDashboard(Long organizationId);
}
