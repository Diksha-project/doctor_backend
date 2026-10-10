package com.doctor.clinic.DoctorClinic.serviceImpl;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Service;

/**
 * Local allow-list gate for the WhatsApp assistant. Unknown requests fail closed
 * and are redirected instead of being passed to the general-purpose model.
 */
@Service
public class ClinicMessageScopeDetector {
    private static final List<String> SCOPE_TERMS = List.of(
            "health", "healthy", "doctor", "dr", "clinic", "hospital", "patient", "medical",
            "medicine", "medication", "tablet", "prescription", "symptom", "symptoms", "pain",
            "hurt", "hurts", "hurting", "ache", "aching", "sore", "sick", "ill", "unwell",
            "fever", "cough", "cold", "headache", "migraine", "nausea", "vomit", "stomach",
            "dizzy", "rash", "allergy", "infection", "wound", "bleeding", "swelling", "breathing",
            "chest", "blood", "pressure", "diabetes",
            "sugar", "pregnancy", "pregnant", "period", "treatment", "diagnosis", "test", "lab",
            "report", "scan", "xray", "x-ray", "dental", "skin", "emergency", "surgery", "vaccine",
            "vaccination", "appointment", "consultation", "consult", "specialist", "availability",
            "available", "slot", "fee", "fees", "charge", "charges", "clinic hours", "visiting hours",
            "schedule", "reschedule", "follow up", "follow-up", "book appointment", "cancel appointment",
            "patient history", "health record", "health report", "message", "conversation", "chat",
            "dashboard", "app", "login", "password", "account", "profile", "whatsapp", "appointment"
    );

    private static final List<String> COURTESY_TERMS = List.of(
            "hi", "hello", "hey", "good morning", "good afternoon", "good evening",
            "thanks", "thank you", "thankyou", "thx", "ty", "bye", "goodbye", "good night", "goodnight"
    );

    public boolean isInScope(String message) {
        if (message == null || message.isBlank()) return false;
        String text = message.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9+#-]", " ").replaceAll("\\s+", " ").trim();
        if (COURTESY_TERMS.contains(text)) return true;
        return SCOPE_TERMS.stream().anyMatch(term -> containsTerm(text, term));
    }

    private boolean containsTerm(String text, String term) {
        String escaped = java.util.regex.Pattern.quote(term);
        return java.util.regex.Pattern.compile("(?<![a-z0-9])" + escaped + "(?![a-z0-9])").matcher(text).find();
    }
}
