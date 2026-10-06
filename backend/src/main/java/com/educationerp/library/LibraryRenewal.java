package com.educationerp.library;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * One extension of a loan.
 *
 * <p>Kept as a row rather than a counter on the issue, because "how many times was this
 * extended, and when" is the first question asked when a book is overdue again.
 */
@Entity
@Table(name = "library_renewals")
@Getter
@Setter
public class LibraryRenewal extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issue_id", nullable = false)
    private LibraryIssue issue;

    @Column(name = "renewed_at", nullable = false)
    private Instant renewedAt = Instant.now();

    @Column(name = "previous_due_at", nullable = false)
    private Instant previousDueAt;

    @Column(name = "new_due_at", nullable = false)
    private Instant newDueAt;
}
