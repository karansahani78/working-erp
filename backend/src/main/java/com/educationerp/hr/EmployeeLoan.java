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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * A loan or salary advance against an employee. The outstanding balance is only ever moved
 * by a payroll run, which is what keeps it in step with the deductions actually taken.
 */
@Entity
@Table(name = "employee_loans",
        uniqueConstraints = @UniqueConstraint(name = "uk_employee_loans_reference",
                columnNames = "reference"))
@Getter
@Setter
public class EmployeeLoan extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_type", nullable = false, length = 20)
    private LoanType loanType;

    @Column(name = "reference", nullable = false, length = 60)
    private String reference;

    @Column(name = "principal", nullable = false, precision = 14, scale = 2)
    private BigDecimal principal;

    @Column(name = "installment_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal installmentAmount;

    @Column(name = "outstanding", nullable = false, precision = 14, scale = 2)
    private BigDecimal outstanding;

    @Column(name = "granted_on", nullable = false)
    private LocalDate grantedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.ACTIVE;

    public enum LoanType {
        LOAN, ADVANCE
    }

    public enum Status {
        ACTIVE, SETTLED, CANCELLED
    }

    public boolean hasOutstanding() {
        return status == Status.ACTIVE && outstanding.compareTo(BigDecimal.ZERO) > 0;
    }

    /** The most a single payroll run may take, so an advance is never over-deducted. */
    public BigDecimal deductibleThisRun() {
        return outstanding.min(installmentAmount);
    }

    public void collect(BigDecimal amount) {
        outstanding = outstanding.subtract(amount);
        if (outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            outstanding = BigDecimal.ZERO;
            status = Status.SETTLED;
        }
    }
}