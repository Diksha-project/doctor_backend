package com.doctor.clinic.DoctorClinic.response;

import java.util.List;

import lombok.Builder;
import lombok.Data;

/** Full detail for the "Request Details" dialog: the request plus its history and chat context. */
@Data
@Builder
public class BookingRequestDetailResponse {
    private BookingRequestResponse request;
    private List<BookingRequestEventResponse> events;
    private List<ConversationMessageResponse> conversation;
}
