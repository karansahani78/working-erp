package com.educationerp.accounting.dto;

import com.educationerp.accounting.AccountingPeriod;
import com.educationerp.accounting.BankAccount;
import com.educationerp.accounting.BankReconciliation;
import com.educationerp.accounting.ChartOfAccounts;
import com.educationerp.accounting.FiscalYear;
import com.educationerp.accounting.JournalEntry;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AccountingDtos {

    private AccountingDtos() {
    }

    public record CreateFiscalYear(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 20) String code,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record FiscalYearResponse(
            UUID id,
            String name,
            String code,
            LocalDate startDate,
            LocalDate endDate,
            FiscalYear.Status status) {
    }

    public record CreateAccountingPeriod(
            @NotNull UUID fiscalYearId,
            @NotBlank @Size(max = 80) String name,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate) {
    }

    public record AccountingPeriodResponse(
            UUID id,
            UUID fiscalYearId,
            String name,
            LocalDate startDate,
            LocalDate endDate,
            AccountingPeriod.Status status) {
    }

    public record CreateBankAccount(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 150) String bankName,
            @NotBlank @Size(max = 60) String accountNumber,
            UUID chartAccountId,
            @Size(max = 3) String currency) {
    }

    public record BankAccountResponse(
            UUID id,
            String name,
            String bankName,
            String accountNumber,
            UUID chartAccountId,
            String currency,
            boolean active) {
    }

    public record StartReconciliation(
            @NotNull LocalDate statementDate,
            @NotNull @DecimalMin("0.00") BigDecimal statementEndingBalance) {
    }

    public record ReconciliationResponse(
            UUID id,
            UUID bankAccountId,
            LocalDate statementDate,
            BigDecimal statementEndingBalance,
            BankReconciliation.Status status,
            UUID reconciledBy,
            Instant reconciledAt) {
    }

    public record CreateAccount(
            @NotBlank @Size(max = 20) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull ChartOfAccounts.AccountGroup accountGroup,
            @NotNull ChartOfAccounts.AccountType accountType,
            UUID parentId,
            Boolean postable) {
    }

    public record AccountResponse(
            UUID id,
            String code,
            String name,
            ChartOfAccounts.AccountGroup accountGroup,
            ChartOfAccounts.AccountType accountType,
            UUID parentId,
            boolean postable,
            boolean active) {
    }

    public record JournalLineRequest(
            @NotNull UUID accountId,
            @Size(max = 200) String description,
            @NotNull @DecimalMin("0.00") BigDecimal debit,
            @NotNull @DecimalMin("0.00") BigDecimal credit,
            @Size(max = 20) String partyType,
            UUID partyId) {
    }

    public record CreateJournalEntry(
            @NotNull LocalDate entryDate,
            @NotNull UUID accountingPeriodId,
            @NotNull JournalEntry.SourceType sourceType,
            UUID sourceId,
            @Size(max = 300) String description,
            @NotNull List<JournalLineRequest> lines) {
    }

    public record JournalEntryResponse(
            UUID id,
            String entryNumber,
            LocalDate entryDate,
            UUID fiscalYearId,
            UUID accountingPeriodId,
            JournalEntry.SourceType sourceType,
            UUID sourceId,
            String description,
            JournalEntry.Status status,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            UUID reversedById,
            Instant postedAt,
            List<JournalLineResponse> lines) {
    }

    public record JournalLineResponse(
            UUID id,
            UUID accountId,
            String accountCode,
            String accountName,
            String description,
            BigDecimal debit,
            BigDecimal credit,
            String partyType,
            UUID partyId) {
    }

    public record TrialBalanceRow(
            UUID accountId,
            String code,
            String name,
            ChartOfAccounts.AccountType accountType,
            BigDecimal debit,
            BigDecimal credit) {
    }

    public record TrialBalance(
            LocalDate asOf,
            BigDecimal totalDebit,
            BigDecimal totalCredit,
            boolean balanced,
            List<TrialBalanceRow> rows) {
    }
}
