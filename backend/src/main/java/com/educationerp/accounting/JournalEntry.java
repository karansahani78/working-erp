package com.educationerp.accounting;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A double-entry record of one financial event.
 *
 * <p>Drafts are editable. Once POSTED the entry is immutable: correcting it means
 * posting a reversing entry, which {@link #reversedById} records.
 */
@Entity
@Table(name = "journal_entries")
public class JournalEntry extends BaseEntity {

    @Column(name = "entry_number", nullable = false, length = 40)
    private String entryNumber;

    @Column(name = "entry_date", nullable = false)
    private LocalDate entryDate;

    @Column(name = "fiscal_year_id")
    private UUID fiscalYearId;

    @Column(name = "accounting_period_id")
    private UUID accountingPeriodId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private SourceType sourceType;

    @Column(name = "source_id")
    private UUID sourceId;

    @Column(name = "description", length = 300)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.DRAFT;

    @Column(name = "posted_at")
    private Instant postedAt;

    @Column(name = "reversed_by_id")
    private UUID reversedById;

    public enum SourceType {
        MANUAL, PAYMENT, REFUND, EXPENSE, INCOME, PAYROLL, OPENING
    }

    public enum Status {
        DRAFT, POSTED, REVERSED
    }

    /**
     * Statuses that make up the ledger. A reversed entry is still ledger history: it is the
     * reversal, booked as an entry of its own, that cancels it. Only drafts are excluded.
     */
    public static List<Status> ledgerStatuses() {
        return List.of(Status.POSTED, Status.REVERSED);
    }

    public String getEntryNumber() {
        return entryNumber;
    }

    public void setEntryNumber(String entryNumber) {
        this.entryNumber = entryNumber;
    }

    public LocalDate getEntryDate() {
        return entryDate;
    }

    public void setEntryDate(LocalDate entryDate) {
        this.entryDate = entryDate;
    }

    public UUID getFiscalYearId() {
        return fiscalYearId;
    }

    public void setFiscalYearId(UUID fiscalYearId) {
        this.fiscalYearId = fiscalYearId;
    }

    public UUID getAccountingPeriodId() {
        return accountingPeriodId;
    }

    public void setAccountingPeriodId(UUID accountingPeriodId) {
        this.accountingPeriodId = accountingPeriodId;
    }

    public SourceType getSourceType() {
        return sourceType;
    }

    public void setSourceType(SourceType sourceType) {
        this.sourceType = sourceType;
    }

    public UUID getSourceId() {
        return sourceId;
    }

    public void setSourceId(UUID sourceId) {
        this.sourceId = sourceId;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Instant getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(Instant postedAt) {
        this.postedAt = postedAt;
    }

    public UUID getReversedById() {
        return reversedById;
    }

    public void setReversedById(UUID reversedById) {
        this.reversedById = reversedById;
    }
}
