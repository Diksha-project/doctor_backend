package com.doctor.clinic.DoctorClinic.service;

import java.time.LocalDate;
import java.util.List;

import com.doctor.clinic.DoctorClinic.request.AvailabilityExceptionRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateAvailabilitySettingsRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateWeeklyAvailabilityRequest;
import com.doctor.clinic.DoctorClinic.response.DoctorAvailabilityResponse;
import com.doctor.clinic.DoctorClinic.response.SlotGenerationResultResponse;

public interface DoctorAvailabilityService {

    DoctorAvailabilityResponse getAvailability(Long doctorId, Long organizationId);

    DoctorAvailabilityResponse updateWeeklySchedule(Long doctorId, Long organizationId,
            UpdateWeeklyAvailabilityRequest request);

    DoctorAvailabilityResponse updateSettings(Long doctorId, Long organizationId,
            UpdateAvailabilitySettingsRequest request);

    DoctorAvailabilityResponse addOrUpdateException(Long doctorId, Long organizationId,
            AvailabilityExceptionRequest request);

    DoctorAvailabilityResponse removeException(Long doctorId, Long organizationId, LocalDate date);

    /**
     * Idempotent: (re)generates bookable {@code DoctorSlot} rows for the given
     * doctor across their configured slot-generation horizon, based on their
     * weekly schedule and any date-specific exceptions. Safe to call repeatedly
     * without creating duplicate slots.
     */
    SlotGenerationResultResponse generateSlots(Long doctorId);

    /** Runs {@link #generateSlots(Long)} for every active doctor. */
    List<SlotGenerationResultResponse> generateSlotsForAllActiveDoctors();
}
