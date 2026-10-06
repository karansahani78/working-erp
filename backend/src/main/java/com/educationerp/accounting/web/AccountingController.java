package com.educationerp.accounting.web;

import com.educationerp.accounting.dto.AccountingDtos;
import com.educationerp.accounting.service.AccountingService;
import com.educationerp.accounting.service.BankAccountService;
import com.educationerp.common.api.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Chart of accounts, fiscal calendar, journal entries and the trial balance. */
@RestController
@RequestMapping("/api/v1/accounting")
@Tag(name = "Accounting")
@RequiredArgsConstructor
public class AccountingController {

    private final AccountingService accounting;
    private final BankAccountService banks;

    // -------------------------------------------------------- chart of accounts

    @GetMapping("/accounts")
    public ApiResponse<List<AccountingDtos.AccountResponse>> accounts() {
        return ApiResponse.ok(accounting.listAccounts());
    }

    @PostMapping("/accounts")
    public ApiResponse<AccountingDtos.AccountResponse> createAccount(
            @Valid @RequestBody AccountingDtos.CreateAccount request) {
        return ApiResponse.ok(accounting.createAccount(request));
    }

    // ------------------------------------------------------------- fiscal years

    @GetMapping("/fiscal-years")
    public ApiResponse<List<AccountingDtos.FiscalYearResponse>> fiscalYears() {
        return ApiResponse.ok(accounting.listFiscalYears());
    }

    @PostMapping("/fiscal-years")
    public ApiResponse<AccountingDtos.FiscalYearResponse> createFiscalYear(
            @Valid @RequestBody AccountingDtos.CreateFiscalYear request) {
        return ApiResponse.ok(accounting.createFiscalYear(request));
    }

    @PostMapping("/fiscal-years/{id}/open")
    public ApiResponse<AccountingDtos.FiscalYearResponse> openFiscalYear(@PathVariable UUID id) {
        return ApiResponse.ok(accounting.openFiscalYear(id));
    }

    @PostMapping("/fiscal-years/{id}/close")
    public ApiResponse<AccountingDtos.FiscalYearResponse> closeFiscalYear(@PathVariable UUID id) {
        return ApiResponse.ok(accounting.closeFiscalYear(id));
    }

    // -------------------------------------------------------- accounting periods

    @GetMapping("/fiscal-years/{id}/periods")
    public ApiResponse<List<AccountingDtos.AccountingPeriodResponse>> periods(@PathVariable UUID id) {
        return ApiResponse.ok(accounting.listPeriods(id));
    }

    @PostMapping("/periods")
    public ApiResponse<AccountingDtos.AccountingPeriodResponse> createPeriod(
            @Valid @RequestBody AccountingDtos.CreateAccountingPeriod request) {
        return ApiResponse.ok(accounting.createPeriod(request));
    }

    @PostMapping("/periods/{id}/close")
    public ApiResponse<AccountingDtos.AccountingPeriodResponse> closePeriod(@PathVariable UUID id) {
        return ApiResponse.ok(accounting.closePeriod(id));
    }

    // ----------------------------------------------------------- journal entries

    @GetMapping("/journal-entries")
    public ApiResponse<List<AccountingDtos.JournalEntryResponse>> entries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(accounting.listEntries(from, to));
    }

    @GetMapping("/journal-entries/{id}")
    public ApiResponse<AccountingDtos.JournalEntryResponse> entry(@PathVariable UUID id) {
        return ApiResponse.ok(accounting.getEntry(id));
    }

    @PostMapping("/journal-entries")
    public ApiResponse<AccountingDtos.JournalEntryResponse> createEntry(
            @Valid @RequestBody AccountingDtos.CreateJournalEntry request) {
        return ApiResponse.ok(accounting.createEntry(request));
    }

    @PostMapping("/journal-entries/{id}/post")
    public ApiResponse<AccountingDtos.JournalEntryResponse> post(@PathVariable UUID id) {
        return ApiResponse.ok(accounting.postEntry(id));
    }

    /** Corrections are made by reversal, never by editing a posted entry. */
    @PostMapping("/journal-entries/{id}/reverse")
    public ApiResponse<AccountingDtos.JournalEntryResponse> reverse(@PathVariable UUID id,
                                                                   @RequestParam(required = false) String reason) {
        return ApiResponse.ok(accounting.reverseEntry(id, reason));
    }

    // ------------------------------------------------------------- trial balance

    @GetMapping("/trial-balance")
    public ApiResponse<AccountingDtos.TrialBalance> trialBalance(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf) {
        return ApiResponse.ok(accounting.trialBalance(asOf == null ? LocalDate.now() : asOf));
    }

    // ------------------------------------------------------------- bank accounts

    @GetMapping("/bank-accounts")
    public ApiResponse<List<AccountingDtos.BankAccountResponse>> bankAccounts() {
        return ApiResponse.ok(banks.list());
    }

    @PostMapping("/bank-accounts")
    public ApiResponse<AccountingDtos.BankAccountResponse> createBankAccount(
            @Valid @RequestBody AccountingDtos.CreateBankAccount request) {
        return ApiResponse.ok(banks.create(request));
    }

    @GetMapping("/bank-accounts/{id}/reconciliations")
    public ApiResponse<List<AccountingDtos.ReconciliationResponse>> reconciliations(@PathVariable UUID id) {
        return ApiResponse.ok(banks.listReconciliations(id));
    }

    @PostMapping("/bank-accounts/{id}/reconciliations")
    public ApiResponse<AccountingDtos.ReconciliationResponse> startReconciliation(
            @PathVariable UUID id, @Valid @RequestBody AccountingDtos.StartReconciliation request) {
        return ApiResponse.ok(banks.start(id, request));
    }

    @PostMapping("/reconciliations/{id}/complete")
    public ApiResponse<AccountingDtos.ReconciliationResponse> completeReconciliation(@PathVariable UUID id) {
        return ApiResponse.ok(banks.complete(id));
    }
}
