package com.educationerp.communication;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientUserIdOrderBySentAtDesc(UUID recipientUserId,
                                                             Pageable pageable);

    Page<Notification> findByRecipientUserIdAndReadAtIsNullOrderBySentAtDesc(UUID recipientUserId,
                                                                            Pageable pageable);

    Optional<Notification> findByIdAndRecipientUserId(UUID id, UUID recipientUserId);

    long countByRecipientUserIdAndReadAtIsNull(UUID recipientUserId);

    boolean existsByRecipientUserIdAndEventCodeAndRelatedTypeAndRelatedId(
            UUID recipientUserId, String eventCode, String relatedType, UUID relatedId);

    long countByNoticeId(UUID noticeId);

    /** The portal inbox: newest first, optionally only what has not been read. */
    @Query("""
            select n from Notification n
            where n.recipientUserId = :recipient
              and (:unreadOnly = false or n.readAt is null)
            order by n.sentAt desc, n.id desc
            """)
    Page<Notification> inbox(@Param("recipient") UUID recipient,
                             @Param("unreadOnly") boolean unreadOnly,
                             Pageable pageable);
}
