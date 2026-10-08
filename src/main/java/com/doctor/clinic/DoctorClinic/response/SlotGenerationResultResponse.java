package com.doctor.clinic.DoctorClinic.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SlotGenerationResultResponse {
    private Long doctorId;
    private int slotsCreated;
    private int slotsRemoved;
    private int daysProcessed;
}
