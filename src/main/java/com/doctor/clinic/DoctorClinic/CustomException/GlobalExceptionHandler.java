package com.doctor.clinic.DoctorClinic.CustomException;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.MethodArgumentNotValidException;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {


    
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex, HttpServletRequest request) {
        log.error("Business exception: {}", ex.getMessage());
        ErrorResponse response = ErrorResponse.of(ex.getStatus(), ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> 
            errors.put(error.getField(), error.getDefaultMessage())
        );
        
        ErrorResponse response = ErrorResponse.validationError("Validation failed", errors, request.getRequestURI());
        return ResponseEntity.status(400).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, HttpServletRequest request) {
        log.error("Unexpected error: ", ex);
        ErrorResponse response = ErrorResponse.of(500, "INTERNAL_ERROR", "Something went wrong", request.getRequestURI());
        return ResponseEntity.status(500).body(response);
    }
    
    
 // Instead of separate classes
//    throw BusinessException.notFound("Doctor", doctorId);
//    throw BusinessException.notFound("Organization", orgId);
//    throw BusinessException.notFound("Appointment", appointmentId);
//    throw BusinessException.duplicate("Email", email);
//    throw BusinessException.duplicate("Mobile", mobile);
//    throw BusinessException.slotNotAvailable();
//    throw BusinessException.invalidCredentials();
//    throw BusinessException.accessDenied();
//    throw BusinessException.subscriptionExpired();
    
    
    //EAANNWLz8YiMBRZA0Qu3Gge7IMCZA1leDIaZCzEhx87tzDVLtQu3O0Tx4FJCKdfVdNlDrtQiIRdKguNcP0CVJZAZB3XlAFTOpCcfA0Ad4qkCIVlKPE2TC006uNwzorEUbucZBTIprcXbq6SQjV2Lhong7evNebrhvpQqVmrkam1V2ieCTTbl8vV3UZAZBwfnLIxfOU38yXGmsl41ZAcZCBhW0cuzZAeZBda1nLig22gOQ9iAbPh48mZCnI7nQ2ZAbM96dLOCryw6CcNAZBcxixOjfUkGJhZBJ
}
