package com.doctor.clinic.DoctorClinic.model;

public enum SubscriptionStatus {

	    TRIAL("Trial"),
	    ACTIVE("Active"),
	    EXPIRED("Expired"),
	    CANCELLED("Cancelled"),
	    SUSPENDED("Suspended");
	    
	    private final String displayName;
	    
	    SubscriptionStatus(String displayName) {
	        this.displayName = displayName;
	    }
	    
	    public String getDisplayName() {
	        return displayName;
	    }
}