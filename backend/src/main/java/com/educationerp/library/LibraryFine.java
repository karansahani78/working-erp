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

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Money owed on a loan: a late return, a lost book, a copy returned damaged.
 *
 * <p>A fine can be paid or waived, never both, and the reason for a waiver is kept. That is
 * the difference between a fair charge and an unexplained one.
 */
@Entity
@Table(name = "library_fines")
@Getter
@Setter
public class LibraryFine extends BaseEntity {

    public enum Status { OUTSTANDING, PAID, WAIVED }

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false)
    private LibraryMember member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "issue_id")
    private LibraryIssue issue;

    @Column(name = "reason", nullable = false, length = 300)
    private String reason;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency = "NPR";

    @Column(name = "assessed_on", nullable = false)
    private LocalDate assessedOn = LocalDate.now();

    @Column(name = "paid_at")
    private java.time.Instant paidAt;

    @Column(name = "waived_at")
    private java.time.Instant waivedAt;

    @Column(name = "waiver_reason", length = 300)
    private String waiverReason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.OUTSTANDING;

    public boolean isSettled() {
        return status != Status.OUTSTANDING;
    }
}
