package com.doctor.clinic.DoctorClinic.repo;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.doctor.clinic.DoctorClinic.entity.PatientChatMessage;

public interface PatientChatMessageRepo extends JpaRepository<PatientChatMessage, Long> {

    interface HistoryRow {
        String getDirection();
        String getMessageText();
        String getMessageType();
    }

    interface TimelineRow {
        Long getId();
        String getDirection();
        String getMessageText();
        String getMessageType();
        String getAttachmentMimeType();
        String getAttachmentName();
        java.time.LocalDateTime getCreatedAt();
        Boolean getHasAttachment();
    }

    List<HistoryRow> findByPatientIdOrderByCreatedAtDesc(Long patientId, Pageable pageable);

    org.springframework.data.domain.Page<PatientChatMessage> findAllByOrderByIdAsc(Pageable pageable);

    @Query("select m.id as id, m.direction as direction, m.messageText as messageText, "
            + "m.messageType as messageType, m.attachmentMimeType as attachmentMimeType, "
            + "m.attachmentName as attachmentName, m.createdAt as createdAt, "
            + "case when m.attachmentData is null then false else true end as hasAttachment "
            + "from PatientChatMessage m where m.patient.id = :patientId order by m.createdAt asc")
    List<TimelineRow> findTimelineByPatientId(@Param("patientId") Long patientId);

    @Query(value = "select m.id as id, m.direction as direction, m.message_text as \"messageText\", "
            + "m.message_type as \"messageType\", m.attachment_mime_type as \"attachmentMimeType\", "
            + "m.attachment_name as \"attachmentName\", m.created_at as \"createdAt\", "
            + "case when m.attachment_data is null then false else true end as \"hasAttachment\" "
            + "from patient_chat_messages m where m.patient_id = :patientId order by m.created_at asc",
            countQuery = "select count(*) from patient_chat_messages m where m.patient_id = :patientId",
            nativeQuery = true)
    Page<TimelineRow> findPagedTimelineByPatientId(@Param("patientId") Long patientId, Pageable pageable);

    @Query(value = "select m.id as id, m.direction as \"direction\", m.message_text as \"messageText\", "
            + "m.message_type as \"messageType\", m.attachment_mime_type as \"attachmentMimeType\", "
            + "m.attachment_name as \"attachmentName\", m.created_at as \"createdAt\", "
            + "case when m.attachment_data is null then false else true end as \"hasAttachment\" "
            + "from patient_chat_messages m where m.patient_id = :patientId and m.id > :afterMessageId "
            + "order by m.id asc",
            countQuery = "select count(*) from patient_chat_messages m where m.patient_id = :patientId and m.id > :afterMessageId",
            nativeQuery = true)
    Page<TimelineRow> findMessagesAfter(@Param("patientId") Long patientId,
                                        @Param("afterMessageId") Long afterMessageId, Pageable pageable);

    @Query("select m.id as id, m.direction as direction, m.messageText as messageText, "
            + "m.messageType as messageType, m.attachmentMimeType as attachmentMimeType, "
            + "m.attachmentName as attachmentName, m.createdAt as createdAt, "
            + "case when m.attachmentData is null then false else true end as hasAttachment "
            + "from PatientChatMessage m where m.patient.id = :patientId order by m.createdAt desc")
    List<TimelineRow> findRecentTimelineByPatientId(@Param("patientId") Long patientId, Pageable pageable);

    @Query("select m from PatientChatMessage m where m.id = :messageId "
            + "and m.patient.id = :patientId and m.patient.organization.id = :organizationId")
    Optional<PatientChatMessage> findAttachmentForOrganization(@Param("messageId") Long messageId,
                                                               @Param("patientId") Long patientId,
                                                               @Param("organizationId") Long organizationId);
}
