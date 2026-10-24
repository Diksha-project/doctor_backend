package com.doctor.clinic.DoctorClinic.prescription;

import com.doctor.clinic.DoctorClinic.entity.Organization;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.OrganizationRepo;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import com.doctor.clinic.DoctorClinic.serviceImpl.CloudflareR2StorageService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service @RequiredArgsConstructor
public class PrescriptionDocumentSettingsService {
    private final PrescriptionDocumentTemplateRepository templates;
    private final OrganizationRepo organizations;
    private final DoctorRepo doctors;
    private final AuthorizationService authorization;
    private final CloudflareR2StorageService storage;
    private final ObjectMapper mapper;

    public record ClinicProfile(Long organizationId, String name, String address, String phone, String email,
            String website, String tagline, boolean hasLogo, String logoUrl) {}
    public record ProfileInput(String name, String address, String phone, String email, String website, String tagline) {}
    public record TemplateInput(UUID id, long version, Long userId, String name, String templateType,
            boolean isDefault, Map<String, Object> configuration) {}
    public record TemplateView(UUID id, long version, Long userId, String name, String templateType,
            String status, boolean isDefault, Map<String, Object> configuration, Instant updatedAt) {}

    @Transactional(readOnly = true)
    public ClinicProfile profile() {
        var user = authorization.requirePermission("SETTINGS:VIEW");
        return profileOf(organizations.findById(user.organizationId()).orElseThrow(this::missing));
    }

    @Transactional
    public ClinicProfile updateProfile(ProfileInput input) {
        var user = authorization.requirePermission("ORGANIZATION:EDIT");
        Organization org = organizations.findById(user.organizationId()).orElseThrow(this::missing);
        String name = clean(input.name(), 150, true, "Clinic name");
        org.setOrganizationName(name);
        org.setClinicAddress(clean(input.address(), 1000, false, "Address"));
        org.setClinicPhone(clean(input.phone(), 40, false, "Phone"));
        org.setClinicEmail(clean(input.email(), 254, false, "Email"));
        org.setClinicWebsite(clean(input.website(), 500, false, "Website"));
        org.setClinicTagline(clean(input.tagline(), 200, false, "Tagline"));
        return profileOf(organizations.save(org));
    }

    @Transactional
    public ClinicProfile uploadLogo(MultipartFile file) {
        var user = authorization.requirePermission("ORGANIZATION:EDIT");
        if (file == null || file.isEmpty()) bad("Logo file is required");
        if (file.getSize() > 1024 * 1024) bad("Logo must be no larger than 1 MB");
        String type = Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);
        if (!Set.of("image/png", "image/jpeg", "image/webp").contains(type)) bad("Logo must be PNG, JPEG, or WebP");
        try {
            byte[] bytes = file.getBytes();
            Organization org = organizations.findById(user.organizationId()).orElseThrow(this::missing);
            String previousObjectKey = org.getLogoObjectKey();
            if (storage.isConfigured()) {
                try {
                    var stored = storage.storeOrganizationLogo(user.organizationId(), bytes, type, file.getOriginalFilename());
                    org.setLogoObjectKey(stored.objectKey());
                    org.setLogoData(null);
                } catch (RuntimeException storageFailure) {
                    org.setLogoObjectKey(null);
                    org.setLogoData(bytes);
                }
            } else {
                org.setLogoObjectKey(null);
                org.setLogoData(bytes);
            }
            org.setLogoContentType(type);
            org.setLogoFileName(Optional.ofNullable(file.getOriginalFilename()).orElse("clinic-logo"));
            ClinicProfile result = profileOf(organizations.save(org));
            deleteLogoQuietly(previousObjectKey);
            return result;
        } catch (java.io.IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Logo could not be read"); }
    }

    @Transactional
    public ClinicProfile removeLogo() {
        var user = authorization.requirePermission("ORGANIZATION:EDIT");
        Organization org = organizations.findById(user.organizationId()).orElseThrow(this::missing);
        String previousObjectKey = org.getLogoObjectKey();
        org.setLogoObjectKey(null); org.setLogoContentType(null); org.setLogoFileName(null); org.setLogoData(null);
        ClinicProfile result = profileOf(organizations.save(org));
        deleteLogoQuietly(previousObjectKey);
        return result;
    }

    @Transactional(readOnly = true)
    public CloudflareR2StorageService.FileContent logo() {
        var user = authorization.requirePermission("SETTINGS:VIEW");
        Organization org = organizations.findById(user.organizationId()).orElseThrow(this::missing);
        if (org.getLogoData() != null && org.getLogoData().length > 0) {
            return new CloudflareR2StorageService.FileContent(org.getLogoData(), org.getLogoContentType(), org.getLogoFileName());
        }
        if (org.getLogoObjectKey() == null) throw missing();
        return storage.read(org.getLogoObjectKey(), org.getLogoContentType(), org.getLogoFileName());
    }

    @Transactional(readOnly = true)
    public List<TemplateView> list() {
        var user = authorization.requirePermission("SETTINGS:VIEW");
        return templates.findByOrganizationIdOrderByIsDefaultDescUpdatedAtDesc(user.organizationId()).stream().map(this::view).toList();
    }

    @Transactional
    public TemplateView save(TemplateInput input) {
        var user = authorization.requireOwnOrganization();
        authorizeEdit(user.organizationId(), input.userId());
        Long doctorId = input.userId() == null ? null : doctors.findByUserIdAndOrganizationId(input.userId(), user.organizationId())
                .orElseThrow(this::missing).getId();
        validate(input);
        PrescriptionDocumentTemplate entity;
        if (input.id() == null) {
            entity = new PrescriptionDocumentTemplate(); entity.setId(UUID.randomUUID()); entity.setOrganizationId(user.organizationId());
        } else {
            entity = templates.findByIdAndOrganizationId(input.id(), user.organizationId()).orElseThrow(this::missing);
            authorizeEdit(user.organizationId(), userIdOf(entity.getDoctorId(), user.organizationId()));
            if (entity.getVersion() != input.version()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Template changed. Reload and retry");
        }
        entity.setDoctorId(doctorId); entity.setName(input.name().trim()); entity.setTemplateType(input.templateType());
        entity.setConfiguration(json(input.configuration()));
        if (entity.getStatus() == null) entity.setStatus("ACTIVE");
        entity.setUpdatedAt(Instant.now());
        if (input.isDefault()) clearDefaults(user.organizationId(), doctorId, entity.getId());
        entity.setDefault(input.isDefault());
        return view(templates.saveAndFlush(entity));
    }

    @Transactional
    public TemplateView duplicate(UUID id) {
        var user = authorization.requireOwnOrganization();
        var source = templates.findByIdAndOrganizationId(id, user.organizationId()).orElseThrow(this::missing);
        authorizeEdit(user.organizationId(), userIdOf(source.getDoctorId(), user.organizationId()));
        var copy = new PrescriptionDocumentTemplate(); copy.setId(UUID.randomUUID()); copy.setOrganizationId(user.organizationId());
        copy.setDoctorId(source.getDoctorId()); copy.setName(uniqueCopyName(source.getName())); copy.setTemplateType(source.getTemplateType());
        copy.setConfiguration(source.getConfiguration()); copy.setStatus("ACTIVE"); copy.setDefault(false);
        return view(templates.saveAndFlush(copy));
    }

    @Transactional
    public TemplateView setStatus(UUID id, String status) {
        var user = authorization.requireOwnOrganization();
        var entity = templates.findByIdAndOrganizationId(id, user.organizationId()).orElseThrow(this::missing);
        authorizeEdit(user.organizationId(), userIdOf(entity.getDoctorId(), user.organizationId()));
        String normalized = Optional.ofNullable(status).orElse("").toUpperCase(Locale.ROOT);
        if (!Set.of("ACTIVE", "ARCHIVED").contains(normalized)) bad("Unsupported template status");
        if ("ARCHIVED".equals(normalized) && entity.isDefault()) bad("Choose another default before archiving this template");
        entity.setStatus(normalized); entity.setUpdatedAt(Instant.now());
        return view(templates.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolveSnapshot(Long organizationId, Long doctorId) {
        List<PrescriptionDocumentTemplate> doctorTemplates = doctorId == null ? List.of()
                : templates.findByOrganizationIdAndDoctorIdAndStatusOrderByIsDefaultDescUpdatedAtDesc(organizationId, doctorId, "ACTIVE");
        List<PrescriptionDocumentTemplate> orgTemplates = templates.findByOrganizationIdAndDoctorIdIsNullAndStatusOrderByIsDefaultDescUpdatedAtDesc(organizationId, "ACTIVE");
        PrescriptionDocumentTemplate selected = doctorTemplates.stream().filter(PrescriptionDocumentTemplate::isDefault).findFirst()
                .orElseGet(() -> orgTemplates.stream().filter(PrescriptionDocumentTemplate::isDefault).findFirst()
                        .orElseGet(() -> orgTemplates.stream().findFirst().orElse(null)));
        Organization org = organizations.findById(organizationId).orElseThrow(this::missing);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clinic", profileOf(org));
        result.put("templateId", selected == null ? null : selected.getId());
        result.put("templateName", selected == null ? "Application default" : selected.getName());
        result.put("configuration", selected == null ? safeDefaults() : read(selected.getConfiguration()));
        return result;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> resolvedForCurrentUser(Long userId) {
        var user = authorization.requirePermission("SETTINGS:VIEW");
        if (userId != null) authorization.requireDoctorPermission("PRESCRIPTIONS:VIEW", user.organizationId(), userId);
        Long doctorId = userId == null ? null : doctors.findByUserIdAndOrganizationId(userId, user.organizationId())
                .orElseThrow(this::missing).getId();
        return resolveSnapshot(user.organizationId(), doctorId);
    }

    private void authorizeEdit(Long organizationId, Long doctorId) {
        if (doctorId == null) authorization.requireOrganizationPermission("ORGANIZATION:EDIT", organizationId);
        else authorization.requireDoctorPermission("PRESCRIPTIONS:EDIT", organizationId, doctorId);
    }
    private void clearDefaults(Long organizationId, Long doctorId, UUID except) {
        templates.findByOrganizationIdOrderByIsDefaultDescUpdatedAtDesc(organizationId).stream()
                .filter(t -> Objects.equals(t.getDoctorId(), doctorId) && !t.getId().equals(except) && t.isDefault())
                .forEach(t -> { t.setDefault(false); templates.save(t); });
    }
    private void validate(TemplateInput input) {
        clean(input.name(), 120, true, "Template name");
        if (!Set.of("GENERAL_OPD", "SPECIALIST").contains(input.templateType())) bad("Unsupported template type");
        if (input.configuration() == null) bad("Template configuration is required");
        Object footer = input.configuration().get("footerText");
        if (footer instanceof String text && text.length() > 1000) bad("Footer text must be 1000 characters or fewer");
        Object margins = input.configuration().get("pageMarginMm");
        if (margins instanceof Number n && (n.intValue() < 8 || n.intValue() > 30)) bad("Page margin must be between 8 and 30 mm");
        Object fontSize = input.configuration().get("fontSize");
        if (fontSize instanceof Number n && (n.intValue() < 9 || n.intValue() > 14)) bad("Font size must be between 9 and 14 pt");
        validateChoice(input.configuration(), "headerAlignment", Set.of("LEFT", "CENTER", "RIGHT"));
        validateChoice(input.configuration(), "pageSize", Set.of("A4", "LETTER"));
        validateColor(input.configuration(), "primaryColor");
        validateColor(input.configuration(), "secondaryColor");
        json(input.configuration());
    }
    private Map<String, Object> safeDefaults() {
        Map<String, Object> defaults = new LinkedHashMap<>();
        defaults.put("headerAlignment", "LEFT"); defaults.put("logoSize", "MEDIUM");
        defaults.put("fontFamily", "Arial"); defaults.put("fontSize", 11); defaults.put("pageMarginMm", 15);
        defaults.put("pageSize", "A4"); defaults.put("primaryColor", "#2563EB"); defaults.put("secondaryColor", "#14B8A6");
        defaults.put("showClinicLogo", true); defaults.put("showClinicName", true);
        defaults.put("showContactDetails", true); defaults.put("showDoctorDetails", true); defaults.put("showDividerLine", true);
        defaults.put("showDoctorPhoto", false); defaults.put("showSpecialty", true);
        defaults.put("showWorkingHours", false); defaults.put("showDisclaimer", true);
        defaults.put("showPrescriptionMeta", true); defaults.put("showPageNumbers", true);
        defaults.put("visibleSections", List.of("complaints", "history", "examination", "assessment",
                "medicines", "investigations", "advice", "followUp"));
        return defaults;
    }
    private Long userIdOf(Long doctorId, Long organizationId) { return doctorId == null ? null : doctors.findByIdAndOrganizationId(doctorId, organizationId).orElseThrow(this::missing).getUser().getId(); }
    private TemplateView view(PrescriptionDocumentTemplate t) { return new TemplateView(t.getId(), t.getVersion(), userIdOf(t.getDoctorId(), t.getOrganizationId()), t.getName(), t.getTemplateType(), t.getStatus(), t.isDefault(), read(t.getConfiguration()), t.getUpdatedAt()); }
    private ClinicProfile profileOf(Organization org) { boolean hasLogo = org.getLogoObjectKey() != null || (org.getLogoData() != null && org.getLogoData().length > 0); return new ClinicProfile(org.getId(), org.getOrganizationName(), org.getClinicAddress(), org.getClinicPhone() == null ? org.getOwnerMobile() : org.getClinicPhone(), org.getClinicEmail() == null ? org.getOwnerEmail() : org.getClinicEmail(), org.getClinicWebsite(), org.getClinicTagline(), hasLogo, hasLogo ? "/prescription-settings/clinic/logo" : null); }
    private String uniqueCopyName(String source) { return (source.length() > 110 ? source.substring(0, 110) : source) + " Copy"; }
    private void deleteLogoQuietly(String objectKey) { try { storage.delete(objectKey); } catch (RuntimeException ignored) { /* Do not undo a successful profile update when stale-asset cleanup fails. */ } }
    private String clean(String value, int max, boolean required, String label) { String clean = value == null ? "" : value.trim(); if (required && clean.isEmpty()) bad(label + " is required"); if (clean.length() > max) bad(label + " is too long"); if (clean.indexOf('<') >= 0 || clean.indexOf('>') >= 0) bad(label + " contains unsupported characters"); return clean; }
    private void validateChoice(Map<String, Object> configuration, String key, Set<String> supported) { Object value = configuration.get(key); if (value != null && !supported.contains(String.valueOf(value))) bad("Unsupported " + key); }
    private void validateColor(Map<String, Object> configuration, String key) { Object value = configuration.get(key); if (value != null && !String.valueOf(value).matches("#[0-9A-Fa-f]{6}")) bad(key + " must be a valid hex color"); }
    private String json(Object value) { try { return mapper.writeValueAsString(value); } catch (JsonProcessingException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Configuration is invalid"); } }
    private Map<String, Object> read(String value) { try { return mapper.readValue(value, new TypeReference<>() {}); } catch (JsonProcessingException e) { throw new IllegalStateException("Stored template is invalid", e); } }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription setting not found"); }
    private void bad(String message) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
}
