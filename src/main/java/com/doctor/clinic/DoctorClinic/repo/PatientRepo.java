package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.doctor.clinic.DoctorClinic.entity.Patient;

public interface PatientRepo extends JpaRepository<Patient, Long> {

    Optional<Patient> findByOrganizationIdAndNormalizedPhone(Long organizationId, String normalizedPhone);

    Optional<Patient> findByIdAndOrganizationId(Long patientId, Long organizationId);

    @Query("select p from Patient p where p.organization.id = :organizationId and "
            + "(:search is null or lower(p.fullName) like lower(concat('%', :search, '%')) "
            + "or (:digits is not null and p.normalizedPhone like concat('%', :digits, '%'))) "
            + "order by p.updatedAt desc")
    List<Patient> searchByOrganization(@Param("organizationId") Long organizationId,
                                       @Param("search") String search,
                                       @Param("digits") String digits);
}
