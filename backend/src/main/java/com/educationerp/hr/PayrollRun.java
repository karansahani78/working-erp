package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * One payroll run for one month. The unique period constraint is what makes reprocessing
 * a month impossible: a second run for the same year and month is refused outright.
 */
@Entity
@Table(name = "payroll_runs",
        uniqueConstraints = @UniqueConstraint(name = "uk_payroll_runs_period",
                columnNames = {"period_year", "period_month"}))
@Getter
@Setter
public class PayrollRun extends BaseEntity {

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "employee_count", nullable = false)
    private int employeeCount;

    @Column(name = "total_gross", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalGross = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_tax", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalTax = BigDecimal.ZERO;

    @Column(name = "total_net", nullable = false, precision = 16, scale = 2)
    private BigDecimal totalNet = BigDecimal.ZERO;

    @Column(name = "processed_at")
    private Instant processedAt;

    @Column(name = "approved_at")
    private Instant approvedAt;

    @Column(name = "approved_by")
    private java.util.UUID approvedBy;

    @Column(name = "notes", length = 500)
    private String notes;

    /** The journal entry this run was booked into, set once and never cleared. */
    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    public enum Status {
        DRAFT, PROCESSED, APPROVED, CANCELLED
    }

    public String period() {
        return periodYear + "-" + String.format("%02d", periodMonth);
    }

    public void approve(java.util.UUID approver) {
        approvedBy = approver;
        approvedAt = Instant.now();
        status = Status.APPROVED;
    }
}