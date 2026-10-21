package com.doctor.clinic.DoctorClinic.prescription;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/prescription-settings") @RequiredArgsConstructor
public class PrescriptionDocumentSettingsController {
    private final PrescriptionDocumentSettingsService service;
    @GetMapping("/clinic") public PrescriptionDocumentSettingsService.ClinicProfile profile() { return service.profile(); }
    @PutMapping("/clinic") public PrescriptionDocumentSettingsService.ClinicProfile updateProfile(@RequestBody PrescriptionDocumentSettingsService.ProfileInput input) { return service.updateProfile(input); }
    @PostMapping(value = "/clinic/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE) public PrescriptionDocumentSettingsService.ClinicProfile uploadLogo(@RequestParam("file") MultipartFile file) { return service.uploadLogo(file); }
    @DeleteMapping("/clinic/logo") public PrescriptionDocumentSettingsService.ClinicProfile removeLogo() { return service.removeLogo(); }
    @GetMapping("/clinic/logo") public ResponseEntity<byte[]> logo() { var file = service.logo(); return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType())).header(HttpHeaders.CACHE_CONTROL, "private, max-age=300").body(file.bytes()); }
    @GetMapping("/templates") public List<PrescriptionDocumentSettingsService.TemplateView> list() { return service.list(); }
    @PostMapping("/templates") public PrescriptionDocumentSettingsService.TemplateView save(@RequestBody PrescriptionDocumentSettingsService.TemplateInput input) { return service.save(input); }
    @PostMapping("/templates/{id}/duplicate") public PrescriptionDocumentSettingsService.TemplateView duplicate(@PathVariable UUID id) { return service.duplicate(id); }
    public record StatusInput(String status) {}
    @PutMapping("/templates/{id}/status") public PrescriptionDocumentSettingsService.TemplateView status(@PathVariable UUID id, @RequestBody StatusInput input) { return service.setStatus(id, input.status()); }
    @GetMapping("/resolved") public Map<String, Object> resolved(@RequestParam(required = false) Long doctorId) { return service.resolvedForCurrentUser(doctorId); }
}
