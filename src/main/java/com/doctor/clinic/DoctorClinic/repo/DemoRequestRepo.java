package com.doctor.clinic.DoctorClinic.repo;

import com.doctor.clinic.DoctorClinic.entity.DemoRequest;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DemoRequestRepo extends JpaRepository<DemoRequest, Long> {
    List<DemoRequest> findAllByOrderByRequestedAtDesc();
    long countByStatus(String status);
}
