package com.doctor.clinic.DoctorClinic.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.doctor.clinic.DoctorClinic.entity.Subscription;


public interface SubscriptionRepo  extends JpaRepository<Subscription, Long>{

}
