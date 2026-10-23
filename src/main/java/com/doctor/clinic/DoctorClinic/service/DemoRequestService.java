package com.doctor.clinic.DoctorClinic.service;

import com.doctor.clinic.DoctorClinic.request.CreateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateDemoRequestRequest;
import com.doctor.clinic.DoctorClinic.response.DemoRequestResponse;
import com.doctor.clinic.DoctorClinic.response.DemoRequestSubmissionResponse;
import java.util.List;

public interface DemoRequestService {
    DemoRequestSubmissionResponse create(CreateDemoRequestRequest request);

    List<DemoRequestResponse> listAll();

    DemoRequestResponse update(Long id, UpdateDemoRequestRequest request);

    long countByStatus(String status);
}
