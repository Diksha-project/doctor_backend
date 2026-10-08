package com.doctor.clinic.DoctorClinic.controller;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.doctor.clinic.DoctorClinic.CustomException.BusinessException;
import com.doctor.clinic.DoctorClinic.model.ApiResponse;
import com.doctor.clinic.DoctorClinic.model.BookingRequestStatus;
import com.doctor.clinic.DoctorClinic.request.OfferSlotsRequest;
import com.doctor.clinic.DoctorClinic.request.UpdateBookingRequestStatusRequest;
import com.doctor.clinic.DoctorClinic.response.AvailableSlotResponse;
import com.doctor.clinic.DoctorClinic.response.BookingRequestDetailResponse;
import com.doctor.clinic.DoctorClinic.response.BookingRequestResponse;
import com.doctor.clinic.DoctorClinic.serviceImpl.BookingRequestService;

import lombok.RequiredArgsConstructor;

/** Patient appointment requests captured when no slots were available. */
@RestController
@RequestMapping("/api/booking-requests")
@RequiredArgsConstructor
public class BookingRequestController {

    private final BookingRequestService bookingRequestService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<BookingRequestResponse>>> list(
            @RequestParam(required = false) BookingRequestStatus status) {
        return ResponseEntity.ok(ApiResponse.success(bookingRequestService.list(organizationId(), status)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<BookingRequestDetailResponse>> detail(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingRequestService.getDetail(id, organizationId())));
    }

    @GetMapping("/{id}/slots")
    public ResponseEntity<ApiResponse<List<AvailableSlotResponse>>> slots(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(bookingRequestService.availableSlots(id, organizationId())));
    }

    @PostMapping("/{id}/offer")
    public ResponseEntity<ApiResponse<BookingRequestResponse>> offer(@PathVariable Long id,
            @Valid @RequestBody OfferSlotsRequest request) {
        BookingRequestResponse response = bookingRequestService.offerSlots(id, organizationId(), request.getSlotIds());
        return ResponseEntity.ok(ApiResponse.success("Options sent to the patient", response));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<ApiResponse<BookingRequestResponse>> updateStatus(@PathVariable Long id,
            @Valid @RequestBody UpdateBookingRequestStatusRequest request) {
        BookingRequestResponse response = bookingRequestService.updateStatus(id, organizationId(),
                request.getStatus(), request.getNote());
        return ResponseEntity.ok(ApiResponse.success("Request updated", response));
    }

    private Long organizationId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Object details = authentication == null ? null : authentication.getDetails();
        if (details instanceof Number number) {
            return number.longValue();
        }
        throw BusinessException.accessDenied();
    }
}
