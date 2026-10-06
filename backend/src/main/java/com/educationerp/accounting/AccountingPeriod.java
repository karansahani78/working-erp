package com.educationerp.accounting;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/** A bookable slice of a fiscal year, such as a month or a quarter. */
@Entity
@Table(name = "accounting_periods")
public class AccountingPeriod extends BaseEntity {

    @Column(name = "fiscal_year_id", nullable = false)
    private UUID fiscalYearId;

    @Column(name = "name", nullable = false, length = 80)
    private String name;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status = Status.OPEN;

    public enum Status {
        OPEN, CLOSED, LOCKED
    }

    public UUID getFiscalYearId() {
        return fiscalYearId;
    }

    public void setFiscalYearId(UUID fiscalYearId) {
        this.fiscalYearId = fiscalYearId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}
