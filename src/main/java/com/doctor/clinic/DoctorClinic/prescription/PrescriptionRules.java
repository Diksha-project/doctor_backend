package com.doctor.clinic.DoctorClinic.prescription;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PrescriptionRules {
    private PrescriptionRules() {}
    public static void validate(PrescriptionContent content, boolean finalize) {
        if (content == null) reject("Prescription content is required");
        if (content.medicines() != null && content.medicines().size() > 100) reject("At most 100 medicines are supported");
        if (content.investigations() != null && content.investigations().size() > 100) reject("At most 100 investigations are supported");
        if (!finalize) return;
        if (blank(content.assessment())) reject("Review and enter the assessment before finalizing");
        if (blank(content.reviewNote())) reject("Confirm clinical review with a review note");
        if (content.medicines() != null) for (var medicine : content.medicines()) {
            if (medicine == null || blank(medicine.name()) || blank(medicine.dose())
                    || blank(medicine.route()) || blank(medicine.frequency()) || blank(medicine.duration()))
                reject("Every medicine requires a reviewed name, dose, route, frequency and duration");
        }
        if (content.investigations() != null) for (var investigation : content.investigations()) {
            if (investigation == null || blank(investigation.name())) reject("Investigation names are required");
        }
    }
    private static boolean blank(String value) { return value == null || value.isBlank(); }
    private static void reject(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
