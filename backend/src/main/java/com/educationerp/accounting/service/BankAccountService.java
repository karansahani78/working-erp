package com.educationerp.accounting.service;

import com.educationerp.accounting.BankAccount;
import com.educationerp.accounting.BankAccountRepository;
import com.educationerp.accounting.BankReconciliation;
import com.educationerp.accounting.BankReconciliationRepository;
import com.educationerp.accounting.ChartOfAccountsRepository;
import com.educationerp.accounting.JournalEntry;
import com.educationerp.accounting.JournalLineRepository;
import com.educationerp.accounting.dto.AccountingDtos;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Institution bank accounts and the statement matching against them.
 *
 * <p>A reconciliation is signed off by comparing the statement's closing balance to the
 * ledger balance for the linked account. If they differ the reconciliation is recorded as a
 * discrepancy rather than closed, so an unmatched item is visible instead of absorbed.
 */
@Service
@RequiredArgsConstructor
public class BankAccountService {

    private final BankAccountRepository accounts;
    private final BankReconciliationRepository reconciliations;
    private final ChartOfAccountsRepository chart;
    private final JournalLineRepository journalLines;
    private final AuditService audit;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public List<AccountingDtos.BankAccountResponse> list() {
        auth.requirePermission("ACCOUNTING_READ");
        return accounts.findByActiveTrueOrderByNameAsc().stream().map(this::toResponse).toList();
    }

    @Transactional
    public AccountingDtos.BankAccountResponse create(AccountingDtos.CreateBankAccount request) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        if (accounts.existsByBankNameIgnoreCaseAndAccountNumber(request.bankName().trim(),
                request.accountNumber().trim())) {
            throw AppException.duplicate("That bank account is already registered.");
        }
        if (request.chartAccountId() != null) {
            chart.findById(request.chartAccountId())
                    .orElseThrow(() -> AppException.notFound("Account"));
        }
        BankAccount account = new BankAccount();
        account.setName(request.name().trim());
        account.setBankName(request.bankName().trim());
        account.setAccountNumber(request.accountNumber().trim());
        account.setChartAccountId(request.chartAccountId());
        account.setCurrency(request.currency() == null || request.currency().isBlank()
                ? "NPR" : request.currency().trim().toUpperCase(java.util.Locale.ROOT));
        account.setActive(true);
        BankAccount saved = accounts.save(account);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("BankAccount")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getName())
                .summary("Registered a bank account at " + saved.getBankName())
                .module("ACCOUNTING")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<AccountingDtos.ReconciliationResponse> listReconciliations(UUID bankAccountId) {
        auth.requirePermission("ACCOUNTING_READ");
        return reconciliations.findByBankAccountIdOrderByStatementDateDesc(bankAccountId).stream()
                .map(this::toReconciliation).toList();
    }

    @Transactional
    public AccountingDtos.ReconciliationResponse start(UUID bankAccountId,
                                                       AccountingDtos.StartReconciliation request) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        BankAccount account = accounts.findById(bankAccountId)
                .orElseThrow(() -> AppException.notFound("Bank account"));
        if (account.getChartAccountId() == null) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Link this bank account to a ledger account before reconciling it.");
        }
        if (reconciliations.findByBankAccountIdOrderByStatementDateDesc(bankAccountId).stream()
                .anyMatch(existing -> request.statementDate().equals(existing.getStatementDate()))) {
            throw AppException.duplicate("A reconciliation already exists for that statement date.");
        }
        BankReconciliation reconciliation = new BankReconciliation();
        reconciliation.setBankAccountId(bankAccountId);
        reconciliation.setStatementDate(request.statementDate());
        reconciliation.setStatementEndingBalance(request.statementEndingBalance());
        reconciliation.setStatus(BankReconciliation.Status.IN_PROGRESS);
        return toReconciliation(reconciliations.save(reconciliation));
    }

    /**
     * Closes a reconciliation. When the statement and the ledger agree the item is closed;
     * when they do not it is filed as a discrepancy so the difference is investigated
     * rather than quietly signed off.
     */
    @Transactional
    public AccountingDtos.ReconciliationResponse complete(UUID id) {
        auth.requirePermission("ACCOUNTING_MANAGE");
        BankReconciliation reconciliation = reconciliations.findById(id)
                .orElseThrow(() -> AppException.notFound("Reconciliation"));
        if (reconciliation.getStatus() != BankReconciliation.Status.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an in-progress reconciliation can be completed.");
        }
        BankAccount account = accounts.findById(reconciliation.getBankAccountId())
                .orElseThrow(() -> AppException.notFound("Bank account"));
        BigDecimal ledger = journalLines.balanceOf(account.getChartAccountId(), JournalEntry.ledgerStatuses());
        boolean agrees = ledger.compareTo(reconciliation.getStatementEndingBalance()) == 0;
        reconciliation.setStatus(agrees
                ? BankReconciliation.Status.RECONCILED : BankReconciliation.Status.DISCREPANCY);
        reconciliation.setReconciledBy(auth.currentUserOrNull() == null ? null : auth.currentUser().userId());
        reconciliation.setReconciledAt(Instant.now());
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("BankReconciliation")
                .entityId(reconciliation.getId().toString())
                .entityLabel(account.getName())
                .summary(agrees
                        ? "Reconciled to the statement"
                        : "Discrepancy: ledger " + ledger.toPlainString() + " against statement "
                                + reconciliation.getStatementEndingBalance().toPlainString())
                .module("ACCOUNTING")
                .succeeded(agrees)
                .build());
        return toReconciliation(reconciliations.save(reconciliation));
    }

    private AccountingDtos.BankAccountResponse toResponse(BankAccount account) {
        return new AccountingDtos.BankAccountResponse(account.getId(), account.getName(),
                account.getBankName(), account.getAccountNumber(), account.getChartAccountId(),
                account.getCurrency(), account.isActive());
    }

    private AccountingDtos.ReconciliationResponse toReconciliation(BankReconciliation reconciliation) {
        return new AccountingDtos.ReconciliationResponse(reconciliation.getId(),
                reconciliation.getBankAccountId(), reconciliation.getStatementDate(),
                reconciliation.getStatementEndingBalance(), reconciliation.getStatus(),
                reconciliation.getReconciledBy(), reconciliation.getReconciledAt());
    }
}
