package com.educationerp.accounting;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** A bank statement matched against the ledger for one account. */
@Entity
@Table(name = "bank_reconciliations")
public class BankReconciliation extends BaseEntity {

    @Column(name = "bank_account_id", nullable = false)
    private UUID bankAccountId;

    @Column(name = "statement_date", nullable = false)
    private LocalDate statementDate;

    @Column(name = "statement_ending_balance", nullable = false, precision = 14, scale = 2)
    private BigDecimal statementEndingBalance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.IN_PROGRESS;

    @Column(name = "reconciled_by")
    private UUID reconciledBy;

    @Column(name = "reconciled_at")
    private Instant reconciledAt;

    public enum Status {
        IN_PROGRESS, RECONCILED, DISCREPANCY
    }

    public UUID getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(UUID bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public LocalDate getStatementDate() {
        return statementDate;
    }

    public void setStatementDate(LocalDate statementDate) {
        this.statementDate = statementDate;
    }

    public BigDecimal getStatementEndingBalance() {
        return statementEndingBalance;
    }

    public void setStatementEndingBalance(BigDecimal statementEndingBalance) {
        this.statementEndingBalance = statementEndingBalance;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public UUID getReconciledBy() {
        return reconciledBy;
    }

    public void setReconciledBy(UUID reconciledBy) {
        this.reconciledBy = reconciledBy;
    }

    public Instant getReconciledAt() {
        return reconciledAt;
    }

    public void setReconciledAt(Instant reconciledAt) {
        this.reconciledAt = reconciledAt;
    }
}
