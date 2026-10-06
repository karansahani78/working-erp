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
 * Something the institution tells everybody, or a group of them, once.
 *
 * <p>Publishing is a separate act from writing, and it is the act that creates a notification
 * per recipient. Until then the notice is a draft nobody has seen.
 */
@Entity
@Table(name = "notices")
@Getter
@Setter
public class Notice extends BaseEntity {

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "body", nullable = false, columnDefinition = "text")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "audience", nullable = false, length = 20)
    private Audience audience = Audience.ALL;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "published_by")
    private UUID publishedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    /** How many people the notice actually reached, kept for reporting rather than guessed at. */
    @Column(name = "recipient_count", nullable = false)
    private int recipientCount;

    public enum Audience {
        ALL,
        STUDENTS,
        PARENTS,
        TEACHERS,
        STAFF
    }

    public enum Status {
        DRAFT,
        PUBLISHED
    }

    public void publish(UUID userId, int recipients) {
        this.status = Status.PUBLISHED;
        this.publishedAt = Instant.now();
        this.publishedBy = userId;
        this.recipientCount = recipients;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && expiresAt.isBefore(now);
    }
}
