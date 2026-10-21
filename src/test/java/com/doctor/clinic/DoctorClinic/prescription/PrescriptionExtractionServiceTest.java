package com.doctor.clinic.DoctorClinic.prescription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import java.util.List;
import org.junit.jupiter.api.Test;

class PrescriptionExtractionServiceTest {
    private final PrescriptionExtractionService service = new PrescriptionExtractionService(
            mock(AuthorizationService.class), mock(DoctorRepo.class), mock(PatientRepo.class), mock(AppointmentRepo.class));

    @Test
    void extractsOnlyExplicitMedicationFieldsAndTests() {
        var current = content("Paracetamol 500 milligrams, one tablet twice daily after food for three days. CBC and CRP tests. Drink adequate water. Follow up after five days.");
        var result = service.parse(current);
        assertThat(result.content().medicines()).hasSize(1);
        var medicine = result.content().medicines().get(0);
        assertThat(medicine.name()).isEqualTo("Paracetamol");
        assertThat(medicine.strength()).isEqualTo("500 milligrams");
        assertThat(medicine.dose()).isEqualTo("one tablet");
        assertThat(medicine.frequency()).isEqualTo("twice daily");
        assertThat(medicine.timing()).isEqualTo("after food");
        assertThat(medicine.duration()).isEqualTo("for three days");
        assertThat(medicine.route()).isBlank();
        assertThat(result.content().investigations()).extracting(PrescriptionContent.Investigation::name)
                .containsExactly("CBC", "CRP");
        assertThat(result.content().advice()).isEqualTo("Drink adequate water");
        assertThat(result.content().followUp()).isEqualTo("Follow up after five days");
        assertThat(result.reviewWarnings()).anyMatch(warning -> warning.contains("route"));
    }

    @Test
    void preservesManualFieldsAndSupportsHindiEnglishTestNames() {
        var current = new PrescriptionContent("Manual complaint", "", "", "Manual assessment", List.of(), List.of(),
                "", "", "CBC और CRP जांच. फॉलो अप सात दिन बाद.", "");
        var result = service.parse(current);
        assertThat(result.content().complaints()).isEqualTo("Manual complaint");
        assertThat(result.content().assessment()).isEqualTo("Manual assessment");
        assertThat(result.content().investigations()).extracting(PrescriptionContent.Investigation::name)
                .containsExactly("CBC", "CRP");
        assertThat(result.content().followUp()).isEqualTo("फॉलो अप सात दिन बाद");
    }

    @Test
    void extractsTheNumberedHinglishPrescriptionWithoutInventingMissingFields() {
        var transcript = "Patient ko cold and fever hai, aur pichhle do din se body pain bhi hai. "
                + "Complaints mein fever, cold aur body ache likhiye. "
                + "Medicine one: Paracetamol 500 mg, one tablet twice daily after food for three days. "
                + "Medicine two: Cetirizine 10 mg, one tablet at night for three days. "
                + "Investigations: CBC and CRP. Advice: drink adequate water and take rest. "
                + "Follow-up after three days if symptoms persist. "
                + "Please review all details before finalizing the prescription.";

        var result = service.parse(content(transcript));

        assertThat(result.content().complaints()).isEqualTo("fever, cold aur body ache");
        assertThat(result.content().medicines()).hasSize(2);
        var paracetamol = result.content().medicines().get(0);
        assertThat(paracetamol.name()).isEqualTo("Paracetamol");
        assertThat(paracetamol.strength()).isEqualTo("500 mg");
        assertThat(paracetamol.dose()).isEqualTo("one tablet");
        assertThat(paracetamol.frequency()).isEqualTo("twice daily");
        assertThat(paracetamol.timing()).isEqualTo("after food");
        assertThat(paracetamol.duration()).isEqualTo("for three days");
        assertThat(paracetamol.route()).isBlank();
        var cetirizine = result.content().medicines().get(1);
        assertThat(cetirizine.name()).isEqualTo("Cetirizine");
        assertThat(cetirizine.strength()).isEqualTo("10 mg");
        assertThat(cetirizine.dose()).isEqualTo("one tablet");
        assertThat(cetirizine.frequency()).isBlank();
        assertThat(cetirizine.timing()).isEqualTo("at night");
        assertThat(cetirizine.duration()).isEqualTo("for three days");
        assertThat(result.content().investigations()).extracting(PrescriptionContent.Investigation::name)
                .containsExactly("CBC", "CRP");
        assertThat(result.content().advice()).isEqualTo("drink adequate water and take rest");
        assertThat(result.content().followUp()).isEqualTo("Follow-up after three days if symptoms persist");
        assertThat(result.reviewWarnings()).anyMatch(warning -> warning.contains("Cetirizine") && warning.contains("frequency"));
    }

    private PrescriptionContent content(String transcript) {
        return new PrescriptionContent("", "", "", "", List.of(), List.of(), "", "", transcript, "");
    }
}
