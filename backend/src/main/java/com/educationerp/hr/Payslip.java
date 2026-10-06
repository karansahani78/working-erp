package com.educationerp.hr;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * What one employee earned and was deducted in one run. The line items are kept as well as
 * the totals, so a disputed payslip can be explained rather than merely asserted.
 */
@Entity
@Table(name = "payslips",
        uniqueConstraints = @UniqueConstraint(name = "uk_payslips_run_employee",
                columnNames = {"payroll_run_id", "employee_id"}))
@Getter
@Setter
public class Payslip extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_run_id", nullable = false)
    private PayrollRun payrollRun;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "basic_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal basicSalary = BigDecimal.ZERO;

    @Column(name = "total_allowances", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAllowances = BigDecimal.ZERO;

    @Column(name = "overtime_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal overtimeAmount = BigDecimal.ZERO;

    @Column(name = "bonus", nullable = false, precision = 14, scale = 2)
    private BigDecimal bonus = BigDecimal.ZERO;

    @Column(name = "loan_deduction", nullable = false, precision = 14, scale = 2)
    private BigDecimal loanDeduction = BigDecimal.ZERO;

    @Column(name = "other_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal otherDeductions = BigDecimal.ZERO;

    @Column(name = "gross_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal grossSalary = BigDecimal.ZERO;

    @Column(name = "tax", nullable = false, precision = 14, scale = 2)
    private BigDecimal tax = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "net_salary", nullable = false, precision = 14, scale = 2)
    private BigDecimal netSalary = BigDecimal.ZERO;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "NPR";

    /** Which version of the tax scale produced this figure, kept for reproducibility. */
    @Column(name = "tax_rule_code", length = 40)
    private String taxRuleCode;

    @Column(name = "overtime_minutes", nullable = false)
    private int overtimeMinutes;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.GENERATED;

    @OneToMany(mappedBy = "payslip", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayslipItem> items = new ArrayList<>();

    public enum Status {
        GENERATED, APPROVED, PAID
    }

    /** Gross is everything earned before tax: basic, allowances, overtime and bonus. */
    public BigDecimal computeGross() {
        return basicSalary.add(totalAllowances).add(overtimeAmount).add(bonus).setScale(2);
    }

    public void addItem(PayslipItem item) {
        item.setPayslip(this);
        items.add(item);
    }
}