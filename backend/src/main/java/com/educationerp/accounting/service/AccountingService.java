package com.educationerp.accounting.service;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.accounting.AccountingPeriod;
import com.educationerp.accounting.AccountingPeriodRepository;
import com.educationerp.accounting.ChartOfAccounts;
import com.educationerp.accounting.ChartOfAccountsRepository;
import com.educationerp.accounting.FiscalYear;
import com.educationerp.accounting.FiscalYearRepository;
import com.educationerp.accounting.JournalEntry;
import com.educationerp.accounting.JournalEntryRepository;
import com.educationerp.accounting.JournalLine;
import com.educationerp.accounting.JournalLineRepository;
import com.educationerp.common.numbering.SequenceNumberGenerator;
import com.educationerp.common.numbering.DocumentSequence;
import com.educationerp.accounting.dto.AccountingDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Double-entry bookkeeping.
 *
 * <p>The invariants that make a ledger worth trusting are enforced here rather than left to
 * the caller: debits must equal credits before an entry can be posted, a posted entry is
 * immutable and is corrected only by a reversing entry, and nothing can be booked into a
 * closed period. A trial balance that does not balance is a hard failure, not a warning,
 * because a ledger that drifts is worse than one that refuses a transaction.
 */
@Service
@RequiredArgsConstructor
public class AccountingService {

    private final ChartOfAccountsRepository accounts;
    private final FiscalYearRepository fiscalYears;
    private final AccountingPeriodRepository periods;
    private final JournalEntryRepository entries;
    private final JournalLineRepository lines;
    private final SequenceNumberGenerator numbers;
    private final AuditService audit;
    private final AuthorizationChecker auth;

    // -------------------------------------------------------- chart of accounts

    @Transactional(readOnly = true)
    public List<AccountingDtos.AccountResponse> listAccounts() {
        auth.requirePermission("ACCOUNTING_READ");
        return accounts.findByActiveTrueOrderByCodeAsc().stream()
                .map(account -> new AccountingDtos.AccountResponse(account.getId(), account.getCode(),
                        account.getName(), account.getAccountGroup(), account.getAccountType(),
                        account.getParentId(), account.isPostable(), account.isActive()))
                .toList();
    }

    @Transactional
    public AccountingDtos.AccountResponse createAccount(AccountingDtos.CreateAccount request) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        String code = request.code().trim().toUpperCase(java.util.Locale.ROOT);
        if (accounts.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("An account with this code already exists.");
        }
        ChartOfAccounts account = new ChartOfAccounts();
        account.setCode(code);
        account.setName(request.name().trim());
        account.setAccountGroup(request.accountGroup());
        account.setAccountType(request.accountType());
        account.setParentId(request.parentId());
        account.setPostable(request.postable() == null || request.postable());
        account.setActive(true);
        ChartOfAccounts saved = accounts.save(account);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Account")
                .entityId(saved.getId().toString())
                .entityLabel(code)
                .summary("Created " + request.accountGroup() + " account " + code)
                .module("ACCOUNTING")
                .succeeded(true)
                .build());
        return toAccountResponse(saved);
    }

    // ------------------------------------------------------------- fiscal years

    @Transactional(readOnly = true)
    public List<AccountingDtos.FiscalYearResponse> listFiscalYears() {
        auth.requirePermission("ACCOUNTING_READ");
        return fiscalYears.findAll().stream().map(this::toFiscalYearResponse).toList();
    }

    @Transactional
    public AccountingDtos.FiscalYearResponse createFiscalYear(AccountingDtos.CreateFiscalYear request) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        if (!request.endDate().isAfter(request.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The fiscal year end date must be after its start date.");
        }
        if (fiscalYears.existsByCodeIgnoreCase(request.code().trim())) {
            throw AppException.duplicate("A fiscal year with this code already exists.");
        }
        if (fiscalYears.findByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                request.startDate(), request.endDate()).stream()
                .anyMatch(existing -> existing.getStatus() == FiscalYear.Status.OPEN)) {
            throw new AppException(ErrorCode.CONFLICTING_OPERATION,
                    "Another fiscal year already covers those dates.");
        }
        FiscalYear year = new FiscalYear();
        year.setName(request.name().trim());
        year.setCode(request.code().trim().toUpperCase(java.util.Locale.ROOT));
        year.setStartDate(request.startDate());
        year.setEndDate(request.endDate());
        year.setStatus(FiscalYear.Status.PLANNED);
        return toFiscalYearResponse(fiscalYears.save(year));
    }

    @Transactional
    public AccountingDtos.FiscalYearResponse openFiscalYear(UUID id) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        FiscalYear year = requireFiscalYear(id);
        if (year.getStatus() != FiscalYear.Status.PLANNED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a planned fiscal year can be opened.");
        }
        if (fiscalYears.findFirstByStatus(FiscalYear.Status.OPEN).isPresent()) {
            throw new AppException(ErrorCode.CONFLICTING_OPERATION,
                    "Another fiscal year is already open. Close it first.");
        }
        year.setStatus(FiscalYear.Status.OPEN);
        return toFiscalYearResponse(fiscalYears.save(year));
    }

    @Transactional
    public AccountingDtos.FiscalYearResponse closeFiscalYear(UUID id) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        FiscalYear year = requireFiscalYear(id);
        if (year.getStatus() != FiscalYear.Status.OPEN) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an open fiscal year can be closed.");
        }
        year.setStatus(FiscalYear.Status.CLOSED);
        return toFiscalYearResponse(fiscalYears.save(year));
    }

    // -------------------------------------------------------- accounting periods

    @Transactional(readOnly = true)
    public List<AccountingDtos.AccountingPeriodResponse> listPeriods(UUID fiscalYearId) {
        auth.requirePermission("ACCOUNTING_READ");
        return periods.findByFiscalYearIdOrderByStartDateAsc(fiscalYearId).stream()
                .map(this::toPeriodResponse).toList();
    }

    @Transactional
    public AccountingDtos.AccountingPeriodResponse createPeriod(
            AccountingDtos.CreateAccountingPeriod request) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        FiscalYear year = requireFiscalYear(request.fiscalYearId());
        if (!request.endDate().isAfter(request.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The period end date must be after its start date.");
        }
        if (request.startDate().isBefore(year.getStartDate()) || request.endDate().isAfter(year.getEndDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The period must fall inside fiscal year " + year.getCode() + ".");
        }
        if (periods.findByFiscalYearIdOrderByStartDateAsc(request.fiscalYearId()).stream()
                .anyMatch(existing -> !(request.endDate().isBefore(existing.getStartDate())
                        || request.startDate().isAfter(existing.getEndDate())))) {
            throw new AppException(ErrorCode.CONFLICTING_OPERATION,
                    "This period overlaps an existing accounting period.");
        }
        AccountingPeriod period = new AccountingPeriod();
        period.setFiscalYearId(request.fiscalYearId());
        period.setName(request.name().trim());
        period.setStartDate(request.startDate());
        period.setEndDate(request.endDate());
        period.setStatus(AccountingPeriod.Status.OPEN);
        return toPeriodResponse(periods.save(period));
    }

    @Transactional
    public AccountingDtos.AccountingPeriodResponse closePeriod(UUID id) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        AccountingPeriod period = periods.findById(id)
                .orElseThrow(() -> AppException.notFound("Accounting period"));
        if (period.getStatus() != AccountingPeriod.Status.OPEN) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an open accounting period can be closed.");
        }
        period.setStatus(AccountingPeriod.Status.CLOSED);
        return toPeriodResponse(periods.save(period));
    }

    // ----------------------------------------------------------- journal entries

    @Transactional(readOnly = true)
    public List<AccountingDtos.JournalEntryResponse> listEntries(LocalDate from, LocalDate to) {
        auth.requirePermission("ACCOUNTING_READ");
        return entries.findByEntryDateBetweenOrderByEntryDateAsc(from, to).stream()
                .map(this::toEntryResponse).toList();
    }

    @Transactional(readOnly = true)
    public AccountingDtos.JournalEntryResponse getEntry(UUID id) {
        auth.requirePermission("ACCOUNTING_READ");
        return toEntryResponse(requireEntry(id));
    }

    /**
     * Creates a draft entry. The draft is rejected outright if debits and credits differ,
     * so an unbalanced entry never reaches the ledger even briefly.
     */
    @Transactional
    public AccountingDtos.JournalEntryResponse createEntry(AccountingDtos.CreateJournalEntry request) {
        auth.requirePermission("ACCOUNTING_POST");
        AccountingPeriod period = periods.findById(request.accountingPeriodId())
                .orElseThrow(() -> AppException.notFound("Accounting period"));
        if (period.getStatus() != AccountingPeriod.Status.OPEN) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Accounting period " + period.getName() + " is closed for posting.");
        }
        FiscalYear year = requireFiscalYear(period.getFiscalYearId());
        if (year.getStatus() != FiscalYear.Status.OPEN) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Fiscal year " + year.getCode() + " is closed for posting.");
        }
        if (request.entryDate().isBefore(period.getStartDate())
                || request.entryDate().isAfter(period.getEndDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The entry date falls outside accounting period " + period.getName() + ".");
        }

        List<JournalLine> prepared = new ArrayList<>();
        for (AccountingDtos.JournalLineRequest line : request.lines()) {
            ChartOfAccounts account = accounts.findById(line.accountId())
                    .orElseThrow(() -> AppException.notFound("Account"));
            if (!account.isPostable()) {
                throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Account " + account.getCode() + " is a heading and cannot be posted to.");
            }
            if (!account.isActive()) {
                throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Account " + account.getCode() + " is inactive.");
            }
            if (line.debit().compareTo(BigDecimal.ZERO) == 0 && line.credit().compareTo(BigDecimal.ZERO) == 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "Every journal line must carry either a debit or a credit.");
            }
            if (line.debit().compareTo(BigDecimal.ZERO) > 0 && line.credit().compareTo(BigDecimal.ZERO) > 0) {
                throw new AppException(ErrorCode.VALIDATION_ERROR,
                        "A journal line cannot be both a debit and a credit.");
            }
            JournalLine entity = new JournalLine();
            entity.setAccountId(account.getId());
            entity.setDescription(line.description());
            entity.setDebit(money(line.debit()));
            entity.setCredit(money(line.credit()));
            entity.setPartyType(line.partyType());
            entity.setPartyId(line.partyId());
            prepared.add(entity);
        }
        BigDecimal debits = prepared.stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = prepared.stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The entry does not balance: debits " + debits.toPlainString()
                            + " against credits " + credits.toPlainString() + ".");
        }

        JournalEntry entry = new JournalEntry();
        entry.setEntryNumber(numbers.next(DocumentSequence.Kind.JOURNAL, request.entryDate()));
        entry.setEntryDate(request.entryDate());
        entry.setFiscalYearId(year.getId());
        entry.setAccountingPeriodId(period.getId());
        entry.setSourceType(request.sourceType());
        entry.setSourceId(request.sourceId());
        entry.setDescription(request.description());
        entry.setStatus(JournalEntry.Status.DRAFT);
        entries.save(entry);

        prepared.forEach(line -> {
            line.setJournalEntryId(entry.getId());
            lines.save(line);
        });
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("JournalEntry")
                .entityId(entry.getId().toString())
                .entityLabel(entry.getEntryNumber())
                .summary("Drafted " + prepared.size() + " lines totalling " + debits.toPlainString())
                .module("ACCOUNTING")
                .succeeded(true)
                .build());
        return toEntryResponse(entries.findById(entry.getId()).orElseThrow());
    }

    @Transactional
    public AccountingDtos.JournalEntryResponse postEntry(UUID id) {
        auth.requirePermission("ACCOUNTING_POST");
        JournalEntry entry = requireEntry(id);
        if (entry.getStatus() == JournalEntry.Status.POSTED) {
            return toEntryResponse(entry);
        }
        if (entry.getStatus() == JournalEntry.Status.REVERSED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A reversed entry cannot be posted.");
        }
        AccountingPeriod period = periods.findById(entry.getAccountingPeriodId())
                .orElseThrow(() -> AppException.notFound("Accounting period"));
        // Closing a fiscal year does not close its periods, so the year has to be checked in
        // its own right or a closed year stays open for posting through its own periods.
        FiscalYear year = fiscalYears.findById(entry.getFiscalYearId())
                .orElseThrow(() -> AppException.notFound("Fiscal year"));
        if (year.getStatus() != FiscalYear.Status.OPEN) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Fiscal year " + year.getName() + " is closed for posting.");
        }
        if (period.getStatus() != AccountingPeriod.Status.OPEN) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Accounting period " + period.getName() + " is closed for posting.");
        }
        List<JournalLine> entryLines = lines.findByJournalEntryIdOrderByIdAsc(id);
        BigDecimal debits = entryLines.stream().map(JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entryLines.stream().map(JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This entry no longer balances and cannot be posted.");
        }
        entry.setStatus(JournalEntry.Status.POSTED);
        entry.setPostedAt(Instant.now());
        audit.record(AuditEvent.builder()
                .action(AuditAction.POST)
                .entityType("JournalEntry")
                .entityId(entry.getId().toString())
                .entityLabel(entry.getEntryNumber())
                .summary("Posted " + debits.toPlainString() + " to the ledger")
                .module("ACCOUNTING")
                .succeeded(true)
                .build());
        return toEntryResponse(entries.save(entry));
    }

    /**
     * Reverses a posted entry with an equal and opposite entry. The original stays exactly
     * as it was, so the ledger still shows what was booked and why it was undone.
     */
    @Transactional
    public AccountingDtos.JournalEntryResponse reverseEntry(UUID id, String reason) {
        auth.requirePermission("ACCOUNTING_POST");
        JournalEntry entry = requireEntry(id);
        if (entry.getStatus() != JournalEntry.Status.POSTED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a posted entry can be reversed.");
        }
        if (entry.getReversedById() != null) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This entry has already been reversed.");
        }
        List<JournalLine> original = lines.findByJournalEntryIdOrderByIdAsc(id);
        if (original.isEmpty()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This entry has no lines to reverse.");
        }
        AccountingPeriod period = periods.findById(entry.getAccountingPeriodId())
                .orElseThrow(() -> AppException.notFound("Accounting period"));
        FiscalYear year = fiscalYears.findById(entry.getFiscalYearId())
                .orElseThrow(() -> AppException.notFound("Fiscal year"));
        if (year.getStatus() != FiscalYear.Status.OPEN
                || period.getStatus() != AccountingPeriod.Status.OPEN) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The year or period this entry sits in is closed, so it cannot be reversed.");
        }

        JournalEntry reversal = new JournalEntry();
        reversal.setEntryNumber(numbers.next(DocumentSequence.Kind.JOURNAL, entry.getEntryDate()));
        // Dated like the entry it unwinds: a reversal can only be booked while the original
        // period is still open, so it necessarily belongs to that same period.
        reversal.setEntryDate(entry.getEntryDate());
        reversal.setFiscalYearId(entry.getFiscalYearId());
        reversal.setAccountingPeriodId(entry.getAccountingPeriodId());
        reversal.setSourceType(JournalEntry.SourceType.MANUAL);
        reversal.setSourceId(entry.getId());
        reversal.setDescription("Reversal of " + entry.getEntryNumber()
                + (reason == null || reason.isBlank() ? "" : ": " + reason.trim()));
        reversal.setStatus(JournalEntry.Status.POSTED);
        reversal.setPostedAt(Instant.now());
        entries.save(reversal);

        for (JournalLine line : original) {
            JournalLine mirror = new JournalLine();
            mirror.setJournalEntryId(reversal.getId());
            mirror.setAccountId(line.getAccountId());
            mirror.setDescription(line.getDescription());
            // Swap the sides rather than negating, so a mirrored line can never carry a
            // negative amount and violate the one-side check.
            mirror.setDebit(line.getCredit());
            mirror.setCredit(line.getDebit());
            mirror.setPartyType(line.getPartyType());
            mirror.setPartyId(line.getPartyId());
            lines.save(mirror);
        }

        entry.setStatus(JournalEntry.Status.REVERSED);
        entry.setReversedById(reversal.getId());
        entries.save(entry);
        audit.record(AuditEvent.builder()
                .action(AuditAction.REVERSE)
                .entityType("JournalEntry")
                .entityId(entry.getId().toString())
                .entityLabel(entry.getEntryNumber())
                .summary("Reversed by " + reversal.getEntryNumber())
                .module("ACCOUNTING")
                .succeeded(true)
                .build());
        return toEntryResponse(entries.findById(reversal.getId()).orElseThrow());
    }

    // ------------------------------------------------------------- trial balance

    @Transactional(readOnly = true)
    public AccountingDtos.TrialBalance trialBalance(LocalDate asOf) {
        auth.requirePermission("ACCOUNTING_READ");
        // A reversed entry stays in the ledger: it is the reversal, an entry of its own,
        // that cancels it. Dropping the original here would leave the reversal's effect
        // standing on its own, which is the opposite of what a reversal is for.
        List<JournalEntry> posted = entries.findByEntryDateBetweenOrderByEntryDateAsc(
                LocalDate.of(1900, 1, 1), asOf).stream()
                .filter(entry -> JournalEntry.ledgerStatuses().contains(entry.getStatus()))
                .toList();
        List<UUID> entryIds = posted.stream().map(JournalEntry::getId).toList();
        List<JournalLine> postedLines = entryIds.isEmpty() ? List.of() : lines.findAll().stream()
                .filter(line -> entryIds.contains(line.getJournalEntryId()))
                .toList();

        List<AccountingDtos.TrialBalanceRow> rows = new ArrayList<>();
        BigDecimal totalDebit = BigDecimal.ZERO;
        BigDecimal totalCredit = BigDecimal.ZERO;
        for (ChartOfAccounts account : accounts.findByActiveTrueOrderByCodeAsc()) {
            BigDecimal debit = postedLines.stream()
                    .filter(line -> account.getId().equals(line.getAccountId()))
                    .map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = postedLines.stream()
                    .filter(line -> account.getId().equals(line.getAccountId()))
                    .map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (debit.compareTo(BigDecimal.ZERO) == 0 && credit.compareTo(BigDecimal.ZERO) == 0) {
                continue;
            }
            totalDebit = totalDebit.add(debit);
            totalCredit = totalCredit.add(credit);
            rows.add(new AccountingDtos.TrialBalanceRow(account.getId(), account.getCode(), account.getName(),
                    account.getAccountType(), debit, credit));
        }
        boolean balanced = totalDebit.compareTo(totalCredit) == 0;
        return new AccountingDtos.TrialBalance(asOf, money(totalDebit), money(totalCredit), balanced, rows);
    }

    // -------------------------------------------------------------------- helpers

    private JournalEntry requireEntry(UUID id) {
        return entries.findById(id).orElseThrow(() -> AppException.notFound("Journal entry"));
    }

    private FiscalYear requireFiscalYear(UUID id) {
        return fiscalYears.findById(id).orElseThrow(() -> AppException.notFound("Fiscal year"));
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, java.math.RoundingMode.HALF_UP);
    }

    private AccountingDtos.AccountResponse toAccountResponse(ChartOfAccounts account) {
        return new AccountingDtos.AccountResponse(account.getId(), account.getCode(), account.getName(),
                account.getAccountGroup(), account.getAccountType(), account.getParentId(),
                account.isPostable(), account.isActive());
    }

    private AccountingDtos.FiscalYearResponse toFiscalYearResponse(FiscalYear year) {
        return new AccountingDtos.FiscalYearResponse(year.getId(), year.getName(), year.getCode(),
                year.getStartDate(), year.getEndDate(), year.getStatus());
    }

    private AccountingDtos.AccountingPeriodResponse toPeriodResponse(AccountingPeriod period) {
        return new AccountingDtos.AccountingPeriodResponse(period.getId(), period.getFiscalYearId(),
                period.getName(), period.getStartDate(), period.getEndDate(), period.getStatus());
    }

    private AccountingDtos.JournalEntryResponse toEntryResponse(JournalEntry entry) {
        List<JournalLine> entryLines = lines.findByJournalEntryIdOrderByIdAsc(entry.getId());
        BigDecimal debits = entryLines.stream().map(JournalLine::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entryLines.stream().map(JournalLine::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AccountingDtos.JournalEntryResponse(
                entry.getId(), entry.getEntryNumber(), entry.getEntryDate(), entry.getFiscalYearId(),
                entry.getAccountingPeriodId(), entry.getSourceType(), entry.getSourceId(),
                entry.getDescription(), entry.getStatus(), money(debits), money(credits),
                debits.compareTo(credits) == 0, entry.getReversedById(), entry.getPostedAt(),
                entryLines.stream().map(line -> {
                    ChartOfAccounts account = accounts.findById(line.getAccountId()).orElse(null);
                    return new AccountingDtos.JournalLineResponse(line.getId(), line.getAccountId(),
                            account == null ? null : account.getCode(),
                            account == null ? null : account.getName(),
                            line.getDescription(), line.getDebit(), line.getCredit(),
                            line.getPartyType(), line.getPartyId());
                }).toList());
    }
}
