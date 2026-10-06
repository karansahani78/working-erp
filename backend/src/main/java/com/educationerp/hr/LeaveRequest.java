package com.educationerp.hr;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** An application for leave. Approval is what draws down the balance. */
@Entity
@Table(name = "leave_requests")
@Getter
@Setter
public class LeaveRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "days", nullable = false, precision = 6, scale = 1)
    private BigDecimal days;

    @Column(name = "reason", length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Column(name = "decided_by")
    private java.util.UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_notes", length = 500)
    private String decisionNotes;

    public enum Status {
        PENDING, APPROVED, REJECTED, CANCELLED
    }

    /** Leave is counted in inclusive calendar days, matching how staff read a request. */
    public static BigDecimal daysBetween(LocalDate from, LocalDate to) {
        return BigDecimal.valueOf(ChronoUnit.DAYS.between(from, to) + 1).setScale(1);
    }

    public boolean overlaps(LocalDate from, LocalDate to) {
        return !from.isAfter(endDate) && !to.isBefore(startDate);
    }
}