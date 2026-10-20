# Prescription Studio Implementation

## Implemented

- Dedicated JPA prescription record referencing the existing organization, doctor, patient and optional appointment.
- Clinical content is a typed Java DTO stored as JSON text; this is a prescription snapshot, not a second patient record.
- Java service authorization on each operation. Own-doctor scope and organization scope use the existing AuthorizationService.
- Patient-only drafts require patient-read permission and, for restricted doctors, an existing doctor/patient appointment association.
- Optional appointments must match the organization, doctor and patient.
- UUID draft IDs are generated once by the client and retained for retries.
- Draft updates require the current optimistic-lock version.
- Finalization requires PRESCRIPTIONS:FINALIZE, the authenticated prescribing doctor, explicit confirmation, an assessment, a clinical review note and complete medication instructions.
- Finalized records cannot be edited. A replacement is a new reviewed draft linked to its immutable original.
- Snapshots preserve patient name, age, gender, doctor name, qualification, registration and organization name.
- List operations are tenant/doctor filtered and database paginated (20 records).
- No native SQL business workflows, stored procedures or triggers were introduced.

## Endpoints

- GET /api/prescriptions?doctorId=...&patientId=...&page=0
- GET /api/prescriptions/{id}
- POST /api/prescriptions (create/update draft)
- POST /api/prescriptions/{id}/finalize (version and confirmed=true)

The new endpoints return explicit DTOs. Existing APIs are unchanged.

## Database / permissions

V20261020_001__prescriptions.sql creates the prescriptions table and scope indexes, and registers VIEW, EDIT and FINALIZE permissions.
No role receives these permissions automatically except the existing super-admin all-permissions mechanism.
An administrator must grant the appropriate doctor role the new permissions; finalization still requires the actor's doctor ID to match the prescribing doctor.
Refresh authentication after changing permissions.

## Frontend

The Prescriptions navigation entry and route are permission controlled. The studio supports real patient selection, draft history, medication ordering, investigations, clinical fields, review, finalization, duplication and replacement drafts.
Allergy information is explicitly unknown because no verified allergy field was found in the patient contract.
The A4 print utility fetches the authorized finalized record again, uses text nodes to avoid HTML injection and prints no application navigation. Users may use the browser's Save as PDF destination. It does not represent a digital signature.

## Not implemented / release blockers

This is an initial implementation, not the complete production-ready voice prescription feature.

- Hosted or browser speech recognition, recording consent, language controls and provider privacy/retention approval.
- Structured AI extraction with source evidence, uncertainty flags and clinician review of proposed changes.
- Server-generated Unicode PDF files and private document storage.
- WhatsApp document upload/send integration, verified-recipient confirmation, messaging-window/template checks, retry state and delivery callbacks.
- Appointment/patient-profile entry links, patient-scoped history search, void workflow and persistent draft-edit audit trail.
- End-to-end browser verification, migration verification against PostgreSQL, clinical validation policy review and production compliance review.

The corresponding unfinished integrations are disabled in the UI. No simulated voice, AI, PDF delivery or WhatsApp success is returned.
No audio is captured or retained by this implementation, and clinical content is not submitted to an AI provider.
No external credentials or production settings were added or changed. Existing Gemini and WhatsApp services were inspected but not reused for clinical data without a dedicated, approved integration.

## Verification

New unit tests cover incomplete drafts, required assessment/review, incomplete medication instructions, reviewed instructions, tenant lookup, wrong-doctor finalization, stale versions and explicit approval.
Existing Maven tests run with an H2 test database. Starting the normal backend may use the shared configured database, so do not start it just to apply this migration without reviewing the target database.
