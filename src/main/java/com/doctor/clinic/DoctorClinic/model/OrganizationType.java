package com.doctor.clinic.DoctorClinic.model;

public enum OrganizationType {
    CLINIC("Clinic", 2),      // Max 2 doctors
    NURSING_HOME("Nursing Home", 10); // Max 10 doctors
    
    private final String displayName;
    private final int defaultMaxDoctors;
    
    OrganizationType(String displayName, int defaultMaxDoctors) {
        this.displayName = displayName;
        this.defaultMaxDoctors = defaultMaxDoctors;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public int getDefaultMaxDoctors() {
        return defaultMaxDoctors;
    }
}