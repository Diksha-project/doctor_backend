package com.doctor.clinic.DoctorClinic.prescription;

import java.util.List;

public record PrescriptionContent(String complaints, String history, String examination, String assessment,
        List<Medication> medicines, List<Investigation> investigations, String advice, String followUp,
        String transcript, String reviewNote) {
    public record Medication(String name, String genericName, String brandName, String strength, String form,
            String dose, String route, String frequency, String timing, String duration, String quantity,
            String instructions) {}
    public record Investigation(String name, String instructions, String timing) {}
}
