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

    private PrescriptionContent content(String transcript) {
        return new PrescriptionContent("", "", "", "", List.of(), List.of(), "", "", transcript, "");
    }
}
