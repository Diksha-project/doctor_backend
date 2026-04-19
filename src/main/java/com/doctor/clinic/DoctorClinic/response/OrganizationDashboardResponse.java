package com.doctor.clinic.DoctorClinic.response;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;
import lombok.Data;


@Data
@Builder
public class OrganizationDashboardResponse {

    
    private Long organizationId;
    private String organizationName;
    private String organizationType;
    private LocalDate currentDate;
    
    // Organization Summary
    private OrganizationSummary summary;
    
    // List of Doctors (click to go to doctor's dashboard)
    private List<DoctorListInfo> doctors;
    

}
