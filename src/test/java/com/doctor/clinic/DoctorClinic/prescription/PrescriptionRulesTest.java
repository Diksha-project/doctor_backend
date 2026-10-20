package com.doctor.clinic.DoctorClinic.prescription;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class PrescriptionRulesTest {
    private PrescriptionContent content(String assessment, String review, List<PrescriptionContent.Medication> medicines) {
        return new PrescriptionContent("", "", "", assessment, medicines, List.of(), "", "", "", review);
    }
    @Test void incompleteDraftCanBeSavedWithoutInventingValues() {
        assertDoesNotThrow(() -> PrescriptionRules.validate(content("", "", List.of()), false));
    }
    @Test void finalizationRequiresAssessmentAndReview() {
        assertThrows(ResponseStatusException.class, () -> PrescriptionRules.validate(content("", "reviewed", List.of()), true));
        assertThrows(ResponseStatusException.class, () -> PrescriptionRules.validate(content("assessment", "", List.of()), true));
    }
    @Test void incompleteMedicationCannotBeFinalized() {
        var medicine = new PrescriptionContent.Medication("Dictated name", "", "", "", "", "", "", "", "", "", "", "");
        assertThrows(ResponseStatusException.class, () -> PrescriptionRules.validate(content("assessment", "reviewed", List.of(medicine)), true));
        assertEquals("", medicine.dose());
    }
    @Test void clinicianReviewedInstructionsCanBeFinalized() {
        var medicine = new PrescriptionContent.Medication("Entered name", "", "", "entered strength", "", "entered dose", "entered route", "entered frequency", "", "entered duration", "", "");
        assertDoesNotThrow(() -> PrescriptionRules.validate(content("assessment", "reviewed", List.of(medicine)), true));
    }
}
