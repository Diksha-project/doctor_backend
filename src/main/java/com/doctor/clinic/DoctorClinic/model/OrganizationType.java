package com.doctor.clinic.DoctorClinic.model;

public enum OrganizationType {
    CLINIC("Clinic", 2),
    NURSING_HOME("Nursing Home", 10),
    HOSPITAL("Hospital", 25);
    
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
