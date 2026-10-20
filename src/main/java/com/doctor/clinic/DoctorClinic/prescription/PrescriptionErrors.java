package com.doctor.clinic.DoctorClinic.prescription;

import java.util.Map;
import org.springframework.core.annotation.Order;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Order(0)
@RestControllerAdvice(assignableTypes = PrescriptionController.class)
public class PrescriptionErrors {
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<Map<String, String>> stale() {
        return ResponseEntity.status(409).body(Map.of("message", "Prescription changed. Reload and review before retrying."));
    }
}
