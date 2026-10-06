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

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * One loan: a copy, a member, and the date it is wanted back.
 *
 * <p>Rows are never deleted when a book comes back. The history of what was lent to whom, and
 * what condition it was in, is the part of a library that matters afterwards.
 */
@Entity
@Table(name = "library_issues")
@Getter
@Setter
public class LibraryIssue extends BaseEntity {

    public enum Status { ISSUED, RETURNED, LOST }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "copy_id", nullable = false)
    private BookCopy copy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private LibraryMember member;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt = Instant.now();

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    @Column(name = "renewal_count", nullable = false)
    private int renewalCount = 0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fine_id")
    private LibraryFine fine;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_out", nullable = false, length = 20)
    private BookCopy.ConditionStatus conditionOut = BookCopy.ConditionStatus.GOOD;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_in", length = 20)
    private BookCopy.ConditionStatus conditionIn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ISSUED;

    public boolean isOverdue(LocalDate on) {
        return returnedAt == null && on.isAfter(LocalDate.ofInstant(dueAt, java.time.ZoneOffset.UTC));
    }

    public long daysOverdue(LocalDate on) {
        if (!isOverdue(on)) {
            return 0;
        }
        return ChronoUnit.DAYS.between(LocalDate.ofInstant(dueAt, java.time.ZoneOffset.UTC), on);
    }

    public long daysLoaned() {
        Instant end = returnedAt == null ? Instant.now() : returnedAt;
        return Duration.between(issuedAt, end).toDays();
    }
}
