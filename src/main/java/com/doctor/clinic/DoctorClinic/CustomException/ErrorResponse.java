package com.doctor.clinic.DoctorClinic.CustomException;



import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {
    
    private boolean success;
    private String message;
    private String errorCode;
    private int status;
    private LocalDateTime timestamp;
    private String path;
    private Map<String, String> validationErrors;
    
    public static ErrorResponse of(int status, String errorCode, String message, String path) {
        ErrorResponse response = new ErrorResponse();
        response.setSuccess(false);
        response.setStatus(status);
        response.setErrorCode(errorCode);
        response.setMessage(message);
        response.setPath(path);
        response.setTimestamp(LocalDateTime.now());
        return response;
    }
    
    public static ErrorResponse validationError(String message, Map<String, String> validationErrors, String path) {
        ErrorResponse response = ErrorResponse.of(400, "VALIDATION_ERROR", message, path);
        response.setValidationErrors(validationErrors);
        return response;
    }
}
