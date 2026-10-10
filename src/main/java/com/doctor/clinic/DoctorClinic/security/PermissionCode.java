package com.doctor.clinic.DoctorClinic.security;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public enum PermissionCode {
    DASHBOARD_VIEW("DASHBOARD", "VIEW", "View dashboard"),
    PATIENTS_VIEW("PATIENTS", "VIEW", "View patients"),
    PATIENTS_CREATE("PATIENTS", "CREATE", "Create patients"),
    PATIENTS_EDIT("PATIENTS", "EDIT", "Edit patients"),
    PATIENTS_DELETE("PATIENTS", "DELETE", "Delete patients"),
    APPOINTMENTS_VIEW("APPOINTMENTS", "VIEW", "View appointments"),
    APPOINTMENTS_CREATE("APPOINTMENTS", "CREATE", "Create appointments"),
    APPOINTMENTS_EDIT("APPOINTMENTS", "EDIT", "Edit appointments"),
    APPOINTMENTS_CANCEL("APPOINTMENTS", "CANCEL", "Cancel appointments"),
    AVAILABILITY_VIEW("AVAILABILITY", "VIEW", "View availability"),
    AVAILABILITY_EDIT("AVAILABILITY", "EDIT", "Edit availability"),
    WHATSAPP_INBOX_VIEW("WHATSAPP_INBOX", "VIEW", "View WhatsApp inbox"),
    WHATSAPP_INBOX_REPLY("WHATSAPP_INBOX", "REPLY", "Reply to WhatsApp messages"),
    WHATSAPP_INBOX_TAKEOVER("WHATSAPP_INBOX", "TAKEOVER", "Take over conversations"),
    WHATSAPP_INBOX_CLOSE("WHATSAPP_INBOX", "CLOSE", "Close conversations"),
    BOOKING_REQUESTS_VIEW("BOOKING_REQUESTS", "VIEW", "View booking requests"),
    BOOKING_REQUESTS_EDIT("BOOKING_REQUESTS", "EDIT", "Edit booking requests"),
    DOCTORS_VIEW("DOCTORS", "VIEW", "View doctors"),
    DOCTORS_EDIT("DOCTORS", "EDIT", "Edit doctors"),
    USERS_VIEW("USERS", "VIEW", "View users"),
    USERS_EDIT("USERS", "EDIT", "Edit users"),
    ROLES_VIEW("ROLES", "VIEW", "View roles"),
    ROLES_EDIT("ROLES", "EDIT", "Edit roles"),
    AI_ASSISTANT_VIEW("AI_ASSISTANT", "VIEW", "View AI assistant"),
    AI_ASSISTANT_CONFIGURE("AI_ASSISTANT", "CONFIGURE", "Configure AI assistant"),
    WHATSAPP_CONFIGURATION_VIEW("WHATSAPP_CONFIGURATION", "VIEW", "View WhatsApp configuration"),
    WHATSAPP_CONFIGURATION_EDIT("WHATSAPP_CONFIGURATION", "EDIT", "Edit WhatsApp configuration"),
    REPORTS_VIEW("REPORTS", "VIEW", "View reports"),
    ORGANIZATION_VIEW("ORGANIZATION", "VIEW", "View organization"),
    ORGANIZATION_EDIT("ORGANIZATION", "EDIT", "Edit organization"),
    SETTINGS_VIEW("SETTINGS", "VIEW", "View settings"),
    SETTINGS_EDIT("SETTINGS", "EDIT", "Edit settings");

    private final String module;
    private final String action;
    private final String description;

    PermissionCode(String module, String action, String description) {
        this.module = module;
        this.action = action;
        this.description = description;
    }

    public String code() { return module + ":" + action; }
    public String module() { return module; }
    public String action() { return action; }
    public String description() { return description; }

    public static Optional<PermissionCode> fromCode(String code) {
        if (code == null) return Optional.empty();
        return Arrays.stream(values()).filter(permission -> permission.code().equals(code.trim())).findFirst();
    }

    public static Set<String> allCodes() {
        return Arrays.stream(values()).map(PermissionCode::code)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }
}
