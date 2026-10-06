package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * A place in the queue for a book that is out.
 *
 * <p>Reservations are held against the title rather than the copy, because which copy comes
 * back first is not something a member should be promised.
 */
@Entity
@Table(name = "library_reservations")
@Getter
@Setter
public class LibraryReservation extends BaseEntity {

    public enum Status { WAITING, FULFILLED, EXPIRED, CANCELLED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private LibraryMember member;

    @Column(name = "reserved_at", nullable = false)
    private Instant reservedAt = Instant.now();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fulfilled_issue_id")
    private LibraryIssue fulfilledIssue;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.WAITING;

    @Column(name = "queue_position", nullable = false)
    private int queuePosition = 1;

    public boolean isExpired(Instant now) {
        return status == Status.WAITING && expiresAt.isBefore(now);
    }
}
