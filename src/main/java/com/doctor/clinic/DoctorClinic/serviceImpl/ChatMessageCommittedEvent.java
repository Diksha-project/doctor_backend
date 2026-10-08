package com.doctor.clinic.DoctorClinic.serviceImpl;

import com.doctor.clinic.DoctorClinic.response.PatientChatRealtimeMessage;

public record ChatMessageCommittedEvent(PatientChatRealtimeMessage message) {}
