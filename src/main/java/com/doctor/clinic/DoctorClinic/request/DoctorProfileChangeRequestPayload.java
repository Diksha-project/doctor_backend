package com.doctor.clinic.DoctorClinic.request;
import java.util.Map;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

@Data
public class DoctorProfileChangeRequestPayload {
    @NotEmpty private Map<String, Object> proposedChanges;
    @NotBlank private String reason;
}
