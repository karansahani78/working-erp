package com.educationerp.communication;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * One message for one person, in one portal.
 *
 * <p>Nothing is deleted when a person reads a notification: the row is stamped instead, so
 * "has this parent seen the absence notice" remains answerable.
 *
 * <p>A notification about a particular thing is written once. The unique index over
 * recipient, event and related record is what stops a re-marked absence or a replayed
 * payment callback from telling somebody the same news twice.
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
public class Notification extends BaseEntity {

    @Column(name = "recipient_user_id", nullable = false)
    private UUID recipientUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    private Channel channel = Channel.IN_APP;

    @Column(name = "event_code", nullable = false, length = 60)
    private String eventCode;

    @Column(name = "subject", nullable = false, length = 200)
    private String subject;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 10)
    private Priority priority = Priority.NORMAL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private Status status = Status.SENT;

    /** What the message is about, so the portal can offer a link to the thing itself. */
    @Column(name = "related_type", length = 60)
    private String relatedType;

    @Column(name = "related_id")
    private UUID relatedId;

    @Column(name = "notice_id")
    private UUID noticeId;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt = Instant.now();

    @Column(name = "read_at")
    private Instant readAt;

    public enum Priority {
        NORMAL,
        HIGH
    }

    public enum Status {
        SENT,
        READ
    }

    public void markRead() {
        this.status = Status.READ;
        this.readAt = Instant.now();
    }

    public boolean isRead() {
        return readAt != null;
    }
}
