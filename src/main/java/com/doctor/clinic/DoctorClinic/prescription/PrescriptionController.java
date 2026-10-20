package com.doctor.clinic.DoctorClinic.prescription;

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/prescriptions") @RequiredArgsConstructor
public class PrescriptionController {
    private final PrescriptionService service;
    @GetMapping public Page<PrescriptionService.View> list(@RequestParam Long doctorId,
            @RequestParam(required = false) Long patientId, @RequestParam(defaultValue = "0") int page) {
        return service.list(doctorId, patientId, page);
    }
    @GetMapping("/{id}") public PrescriptionService.View get(@PathVariable UUID id) { return service.get(id); }
    @PostMapping public PrescriptionService.View save(@RequestBody PrescriptionService.Draft input) { return service.save(input); }
    public record Approval(long version, boolean confirmed) {}
    @PostMapping("/{id}/finalize") public PrescriptionService.View finalizePrescription(@PathVariable UUID id, @RequestBody Approval input) {
        return service.finalizePrescription(id, input.version(), input.confirmed());
    }
}
