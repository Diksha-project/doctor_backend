package com.doctor.clinic.DoctorClinic.prescription;

import com.doctor.clinic.DoctorClinic.model.ResourceScope;
import com.doctor.clinic.DoctorClinic.repo.AppointmentRepo;
import com.doctor.clinic.DoctorClinic.repo.DoctorRepo;
import com.doctor.clinic.DoctorClinic.repo.PatientRepo;
import com.doctor.clinic.DoctorClinic.security.AuthorizationService;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class PrescriptionExtractionService {
    private static final int MAX_TRANSCRIPT_LENGTH = 20_000;
    private static final Pattern MEDICINE = Pattern.compile(
            "(?iu)^(?:(?:give|start|continue|prescribe|take|दें|शुरू करें|लेना|लीजिए)\\s+)?"
            + "([\\p{L}\\p{M}][\\p{L}\\p{M}\\-]*(?:\\s+[\\p{L}\\p{M}][\\p{L}\\p{M}\\-]*){0,2})\\s+"
            + "(\\d+(?:\\.\\d+)?\\s*(?:mg|mcg|µg|g|ml|iu|units?|milligrams?|micrograms?|grams?|milliliters?|मिग्रा|मिलीग्राम))\\b(.*)$");
    private static final Pattern TESTS = Pattern.compile(
            "(?iu)([\\p{L}\\p{M}0-9+\\-\\s,/&]+?)\\s+(?:tests?|जांच|जाँच)\\b");

    private final AuthorizationService authorization;
    private final DoctorRepo doctors;
    private final PatientRepo patients;
    private final AppointmentRepo appointments;

    public record Request(Long userId, Long patientId, PrescriptionContent content) {}
    public record Result(PrescriptionContent content, List<String> reviewWarnings) {}

    public Result extract(Request request) {
        validateAccess(request);
        return parse(request.content());
    }

    Result parse(PrescriptionContent current) {
        String transcript = clean(current.transcript());
        if (transcript.isBlank()) throw bad("A dictation transcript is required");
        if (transcript.length() > MAX_TRANSCRIPT_LENGTH) throw bad("Dictation transcript is too long");

        var warnings = new LinkedHashSet<String>();
        var medicines = new ArrayList<>(safe(current.medicines()));
        var investigations = new ArrayList<>(safe(current.investigations()));
        String complaints = clean(current.complaints());
        String history = clean(current.history());
        String examination = clean(current.examination());
        String assessment = clean(current.assessment());
        String advice = clean(current.advice());
        String followUp = clean(current.followUp());

        for (String raw : transcript.split("(?<=[.!?।])\\s+|[\\r\\n]+")) {
            String sentence = clean(raw).replaceFirst("[.!?।]+$", "").trim();
            if (sentence.isBlank()) continue;

            String value;
            if (complaints.isBlank() && !(value = complaintInstruction(sentence)).isBlank()) {
                complaints = value;
                continue;
            }
            if (complaints.isBlank() && !(value = labelled(sentence, "(?:chief\\s+)?complaints?")).isBlank()) {
                complaints = value;
                continue;
            }
            if (history.isBlank() && !(value = labelled(sentence, "(?:clinical\\s+)?history")).isBlank()) {
                history = value;
                continue;
            }
            if (examination.isBlank() && !(value = labelled(sentence, "(?:on\\s+)?examination(?:\\s+findings)?")).isBlank()) {
                examination = value;
                continue;
            }
            if (assessment.isBlank() && !(value = labelled(sentence, "(?:diagnosis|assessment)")).isBlank()) {
                assessment = value;
                continue;
            }

            String medicineSentence = sentence.replaceFirst(
                    "(?iu)^medicine\\s+(?:one|two|three|four|five|1|2|3|4|5)\\s*[:\\-]\\s*", "");
            Matcher medicineMatch = MEDICINE.matcher(medicineSentence);
            if (medicineMatch.matches()) {
                String details = clean(medicineMatch.group(3));
                var medicine = new PrescriptionContent.Medication(
                        clean(medicineMatch.group(1)), "", "", clean(medicineMatch.group(2)),
                        first(details, "(?iu)\\b(tablets?|capsules?|syrup|injections?|cream|ointment|drops?|puffs?|गोली|कैप्सूल|सिरप)\\b"),
                        first(details, "(?iu)\\b(?:one|two|half|quarter|एक|दो|आधी|\\d+(?:\\.\\d+)?)\\s*(?:tablets?|capsules?|spoons?|drops?|puffs?|गोली|कैप्सूल)\\b"),
                        first(details, "(?iu)\\b(?:oral|orally|topical|topically|intravenous|intramuscular|sublingual|by mouth|मुंह से)\\b"),
                        first(details, "(?iu)\\b(?:once daily|twice daily|thrice daily|once a day|two times a day|three times a day|every \\d+ hours?|दिन में (?:एक|दो|तीन|\\d+) बार|सुबह शाम)\\b"),
                        first(details, "(?iu)\\b(?:before food|after food|with food|empty stomach|at night|in the morning|at bedtime|खाने से पहले|खाने के बाद|खाने के साथ|खाली पेट|रात में|सुबह)\\b"),
                        first(details, "(?iu)\\b(?:for\\s+)?(?:one|two|three|four|five|six|seven|\\d+)\\s*(?:days?|weeks?|months?|दिन|हफ्ते|सप्ताह|महीने)\\b"),
                        first(details, "(?iu)\\b(?:quantity|qty)\\s*[:\\-]?\\s*\\d+\\b"), "");
                if (!medicines.contains(medicine)) medicines.add(medicine);
                requiredMedicineWarnings(medicine, warnings);
                continue;
            }

            String investigationLabel = labelled(sentence, "(?:investigations?|tests?)");
            if (!investigationLabel.isBlank()) {
                addInvestigations(investigations, investigationLabel);
                continue;
            }
            Matcher testsMatch = TESTS.matcher(sentence);
            if (testsMatch.find()) {
                String names = testsMatch.group(1).replaceFirst("(?iu)^(?:order|do|get|please|कराएं|करवाएं)\\s+", "").trim();
                addInvestigations(investigations, names);
                continue;
            }

            if (advice.isBlank() && !(value = labelled(sentence, "advice")).isBlank()) {
                advice = value;
                continue;
            }
            if (followUp.isBlank() && contains(sentence, "follow up", "follow-up", "फॉलो अप", "फॉलो-अप")) {
                followUp = sentence;
                continue;
            }
            if (advice.isBlank() && startsWith(sentence, "drink ", "rest ", "avoid ", "hydrate ",
                    "पानी ", "आराम ", "परहेज ")) advice = sentence;
        }

        if (medicines.isEmpty() && investigations.isEmpty() && advice.isBlank() && followUp.isBlank()
                && complaints.isBlank() && history.isBlank() && examination.isBlank() && assessment.isBlank()) {
            warnings.add("No confidently structured fields were found. Keep the transcript and enter unclear details manually.");
        }
        return new Result(new PrescriptionContent(complaints, history, examination, assessment,
                medicines, investigations, advice, followUp, current.transcript(), current.reviewNote()),
                List.copyOf(warnings));
    }

    private void validateAccess(Request request) {
        if (request == null || request.userId() == null || request.patientId() == null || request.content() == null)
            throw bad("Doctor, patient and prescription content are required");
        var user = authorization.requireOwnOrganization();
        authorization.requireDoctorPermission("PRESCRIPTIONS:EDIT", user.organizationId(), request.userId());
        var doctor = doctors.findByUserIdAndOrganizationId(request.userId(), user.organizationId()).orElseThrow(this::missing);
        patients.findByIdAndOrganizationId(request.patientId(), user.organizationId()).orElseThrow(this::missing);
        authorization.requireOrganizationPermission("PATIENTS:VIEW", user.organizationId());
        if (!user.hasScope(ResourceScope.ORGANIZATION)
                && !appointments.existsByOrganizationIdAndDoctorIdAndPatientId(
                        user.organizationId(), doctor.getId(), request.patientId())) throw missing();
    }

    private void requiredMedicineWarnings(PrescriptionContent.Medication medicine, Set<String> warnings) {
        var missing = new ArrayList<String>();
        if (clean(medicine.dose()).isBlank()) missing.add("dose");
        if (clean(medicine.route()).isBlank()) missing.add("route");
        if (clean(medicine.frequency()).isBlank()) missing.add("frequency");
        if (clean(medicine.duration()).isBlank()) missing.add("duration");
        if (!missing.isEmpty()) warnings.add("Review medicine “" + medicine.name() + "”: add " + String.join(", ", missing) + ".");
    }

    private String labelled(String sentence, String labelPattern) {
        Matcher match = Pattern.compile("(?iu)^" + labelPattern + "\\s*[:\\-]\\s*(.+)$").matcher(sentence);
        return match.matches() ? clean(match.group(1)) : "";
    }

    private String complaintInstruction(String sentence) {
        Matcher match = Pattern.compile(
                "(?iu)^complaints?\\s+(?:mein|me|में)\\s+(.+?)\\s+(?:likhiye|likhen|लिखिए|लिखें)$")
                .matcher(sentence);
        return match.matches() ? clean(match.group(1)) : "";
    }

    private void addInvestigations(List<PrescriptionContent.Investigation> investigations, String names) {
        for (String name : names.split("(?iu)\\s*(?:,|&|/|\\band\\b|और)\\s*")) {
            String testName = clean(name);
            if (!testName.isBlank()) {
                var test = new PrescriptionContent.Investigation(testName, "", "");
                if (!investigations.contains(test)) investigations.add(test);
            }
        }
    }

    private String first(String source, String regex) {
        Matcher match = Pattern.compile(regex).matcher(source);
        return match.find() ? clean(match.group()) : "";
    }

    private boolean contains(String value, String... terms) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String term : terms) if (normalized.contains(term.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private boolean startsWith(String value, String... terms) {
        String normalized = value.toLowerCase(Locale.ROOT);
        for (String term : terms) if (normalized.startsWith(term.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    private String clean(String value) { return value == null ? "" : value.trim().replaceAll("\\s+", " "); }
    private <T> List<T> safe(List<T> values) { return values == null ? List.of() : values; }
    private ResponseStatusException bad(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }
    private ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Prescription association not found"); }
}
