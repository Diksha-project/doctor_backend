package com.doctor.clinic.DoctorClinic.CustomException;


public class BusinessException extends RuntimeException {
    
    private final int status;
    private final String errorCode;
    
    public BusinessException(int status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }
    
    public int getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    
    // Factory methods for common exceptions
    public static BusinessException notFound(String entity, Object id) {
        return new BusinessException(404, "NOT_FOUND", entity + " not found with ID: " + id);
    }
    
    public static BusinessException duplicate(String field, String value) {
        return new BusinessException(409, "DUPLICATE", field + " already exists: " + value);
    }
    
    public static BusinessException slotNotAvailable() {
        return new BusinessException(400, "SLOT_NOT_AVAILABLE", "Time slot is already booked");
    }
    
    public static BusinessException invalidCredentials() {
        return new BusinessException(401, "INVALID_CREDENTIALS", "Invalid email or password");
    }
    
    public static BusinessException accessDenied() {
        return new BusinessException(403, "ACCESS_DENIED", "You don't have permission");
    }
    
    public static BusinessException subscriptionExpired() {
        return new BusinessException(400, "SUBSCRIPTION_EXPIRED", "Your subscription has expired");
    }
}
