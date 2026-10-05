package com.doctor.clinic.DoctorClinic.repo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.doctor.clinic.DoctorClinic.entity.WhatsAppMessage;
import com.doctor.clinic.DoctorClinic.model.MessageDirection;
import com.doctor.clinic.DoctorClinic.model.MessageStatus;

public interface WhatsAppMessageRepo extends JpaRepository<WhatsAppMessage, Long> {

    List<WhatsAppMessage> findByConversationIdOrderByCreatedAtAsc(Long conversationId);

    List<WhatsAppMessage> findByOrganizationIdAndConversationIdOrderByCreatedAtAsc(Long organizationId, Long conversationId);

    List<WhatsAppMessage> findByOrganizationIdAndConversationIdOrderByCreatedAtDesc(
            Long organizationId, Long conversationId, Pageable pageable);

    Optional<WhatsAppMessage> findByMetaMessageId(String metaMessageId);

    long countByOrganizationIdAndConversationIdAndDirectionAndStatusAndCreatedAtGreaterThanEqual(
            Long organizationId, Long conversationId, MessageDirection direction, MessageStatus status,
            LocalDateTime createdAt);

    @Modifying(flushAutomatically = true)
    @Query("update WhatsAppMessage m set m.status = :readStatus, m.readAt = :readAt "
            + "where m.organization.id = :organizationId and m.conversation.id = :conversationId "
            + "and m.direction = :inboundDirection and m.status = :receivedStatus")
    int markInboundMessagesRead(@Param("organizationId") Long organizationId,
                                @Param("conversationId") Long conversationId,
                                @Param("inboundDirection") MessageDirection inboundDirection,
                                @Param("receivedStatus") MessageStatus receivedStatus,
                                @Param("readStatus") MessageStatus readStatus,
                                @Param("readAt") LocalDateTime readAt);

    long countByConversationIdAndStatusNot(Long conversationId, com.doctor.clinic.DoctorClinic.model.MessageStatus status);

    List<WhatsAppMessage> findByConversationIdOrderByCreatedAtDesc(Long conversationId, Pageable pageable);
}
