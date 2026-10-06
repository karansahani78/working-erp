package com.educationerp.hr;

import com.educationerp.accounting.AccountingPeriod;
import com.educationerp.accounting.AccountingPeriodRepository;
import com.educationerp.accounting.ChartOfAccounts;
import com.educationerp.accounting.ChartOfAccountsRepository;
import com.educationerp.accounting.JournalEntryRepository;
import com.educationerp.accounting.JournalEntry;
import com.educationerp.accounting.dto.AccountingDtos;
import com.educationerp.accounting.service.AccountingService;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.common.error.AppException;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.institution.ModuleSetting;
import com.educationerp.institution.ModuleSettingRepository;
import com.educationerp.common.error.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Salary structures, tax rules and the monthly payroll run.
 *
 * <p>Everything a payslip is made of is configured rather than hardcoded: the basic figure
 * and its components come from a salary structure, tax comes from a versioned bracket table,
 * overtime is derived from attendance, and loan recovery comes from the loan balance. A run
 * is a single transaction over one period, so a month is either fully processed or not at
 * all, and the unique period constraint means it cannot be processed twice.
 */
@Service
@RequiredArgsConstructor
public class PayrollService {

    /** The settings key holding the chart-of-accounts codes payroll posts to. */
    private static final String LEDGER_ACCOUNTS_KEY = "ledgerAccounts";

    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal MONTHS = BigDecimal.valueOf(12);

    private final PayrollRunRepository runs;
    private final PayslipRepository payslips;
    private final SalaryStructureRepository salaryStructures;
    private final TaxRuleRepository taxRules;
    private final EmployeeRepository employees;
    private final EmployeeAttendanceRepository attendance;
    private final EmployeeLoanRepository loans;
    private final AuthorizationChecker auth;
    private final AuditService audit;
    private final InstitutionService institutions;
    private final ModuleSettingRepository moduleSettings;
    private final AccountingPeriodRepository accountingPeriods;
    private final ChartOfAccountsRepository accounts;
    private final JournalEntryRepository journalEntries;
    private final AccountingService accounting;
    private final ObjectMapper mapper;

    // ------------------------------------------------------- salary structures

    @Transactional(readOnly = true)
    public List<HrDtos.SalaryStructureResponse> listSalaryStructures(SalaryStructure.Status status) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("SALARY_MANAGE");
        List<SalaryStructure> found = status != null
                ? salaryStructures.findByStatusOrderByNameAsc(status)
                : salaryStructures.findAll();
        return found.stream().map(this::toSalaryStructureResponse).toList();
    }

    @Transactional
    public HrDtos.SalaryStructureResponse createSalaryStructure(HrDtos.CreateSalaryStructure request) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("SALARY_MANAGE");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (salaryStructures.findByCodeIgnoreCase(code).isPresent()) {
            throw AppException.duplicate("A salary structure with the code " + code + " already exists.");
        }
        BigDecimal overtimeRate = request.overtimeRate() == null ? BigDecimal.ZERO : request.overtimeRate();
        SalaryStructure structure = new SalaryStructure();
        structure.setCode(code);
        structure.setName(request.name().trim());
        structure.setBasicSalary(money(request.basicSalary()));
        structure.setCurrency(request.currency() == null ? "NPR" : request.currency());
        structure.setOvertimeRate(money(overtimeRate));
        structure.setEffectiveFrom(request.effectiveFrom());
        structure.setDescription(trimToNull(request.description()));
        // A structure is draft until published, so a half-built one can never reach a payslip.
        structure.setStatus(SalaryStructure.Status.DRAFT);
        if (request.components() != null) {
            for (HrDtos.SalaryComponentRequest component : request.components()) {
                SalaryComponent line = new SalaryComponent();
                line.setSalaryStructure(structure);
                line.setName(component.name().trim());
                line.setComponentType(component.componentType());
                line.setValueType(component.valueType() == null
                        ? SalaryComponent.ValueType.FIXED : component.valueType());
                if (line.getValueType() == SalaryComponent.ValueType.PERCENTAGE
                        && component.value().compareTo(ONE_HUNDRED) > 0) {
                    throw AppException.rule("The percentage component " + component.name()
                            + " cannot exceed 100.");
                }
                line.setValue(component.value());
                line.setTaxable(component.taxable());
                structure.getComponents().add(line);
            }
        }
        // Saved once with its components, so a structure is never stored half configured.
        return toSalaryStructureResponse(salaryStructures.save(structure));
    }

    /**
     * Publishing is a one-way door in practice: an already published structure is refused,
     * because changing a live one would silently move everyone's next payslip. A raise is
     * made by publishing a new version and pointing employees at it.
     */
    @Transactional
    public HrDtos.SalaryStructureResponse publishSalaryStructure(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("SALARY_MANAGE");
        SalaryStructure structure = salaryStructures.findWithComponentsById(id)
                .orElseThrow(() -> AppException.notFound("Salary structure"));
        if (structure.getStatus() == SalaryStructure.Status.PUBLISHED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This salary structure is already published. Publish a new version instead.");
        }
        structure.setStatus(SalaryStructure.Status.PUBLISHED);
        return toSalaryStructureResponse(salaryStructures.save(structure));
    }

    // -------------------------------------------------------------- tax rules

    @Transactional(readOnly = true)
    public List<HrDtos.TaxRuleResponse> listTaxRules() {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        return taxRules.findAll().stream().map(this::toTaxRuleResponse).toList();
    }

    @Transactional
    public HrDtos.TaxRuleResponse createTaxRule(HrDtos.CreateTaxRule request) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("SALARY_MANAGE");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (taxRules.findByCodeIgnoreCase(code).isPresent()) {
            throw AppException.duplicate("A tax rule with the code " + code + " already exists.");
        }
        if (request.effectiveTo() != null && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw AppException.rule("The end of a tax rule cannot precede its start.");
        }
        List<TaxRule.Bracket> brackets = new ArrayList<>();
        BigDecimal previousBound = null;
        for (HrDtos.TaxBracketRequest band : request.brackets()) {
            if (band.upTo() != null) {
                if (previousBound != null && band.upTo().compareTo(previousBound) <= 0) {
                    throw AppException.rule("Tax brackets must rise: " + band.upTo()
                            + " does not follow " + previousBound + ".");
                }
                previousBound = band.upTo();
            }
            brackets.add(new TaxRule.Bracket(band.upTo(), band.rate()));
        }
        // Only the last band may be open ended, otherwise the brackets would overlap.
        for (int index = 0; index < brackets.size() - 1; index++) {
            if (brackets.get(index).upTo() == null) {
                throw AppException.rule("Only the final tax bracket may be open ended.");
            }
        }
        TaxRule rule = new TaxRule();
        rule.setCode(code);
        rule.setName(request.name().trim());
        rule.setBrackets(writeBrackets(brackets));
        rule.setEffectiveFrom(request.effectiveFrom());
        rule.setEffectiveTo(request.effectiveTo());
        rule.setDescription(trimToNull(request.description()));
        rule.setStatus(TaxRule.Status.ACTIVE);
        return toTaxRuleResponse(taxRules.save(rule));
    }

    // ----------------------------------------------------- loans and advances

    @Transactional
    public HrDtos.LoanResponse grantLoan(HrDtos.GrantLoan request) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("SALARY_MANAGE");
        Employee employee = employees.findById(request.employeeId())
                .orElseThrow(() -> AppException.notFound("Employee"));
        String reference = request.reference() == null || request.reference().isBlank()
                ? defaultReference(request.loanType(), employee.getEmployeeCode(), request.grantedOn())
                : request.reference().trim().toUpperCase(Locale.ROOT);
        if (loans.findByReferenceIgnoreCase(reference).isPresent()) {
            throw AppException.duplicate("A loan with the reference " + reference + " already exists.");
        }
        if (request.installmentAmount().compareTo(request.principal()) > 0) {
            throw AppException.rule("The installment cannot be larger than the amount advanced.");
        }
        EmployeeLoan loan = new EmployeeLoan();
        loan.setEmployee(employee);
        loan.setLoanType(request.loanType());
        loan.setReference(reference);
        loan.setPrincipal(money(request.principal()));
        loan.setInstallmentAmount(money(request.installmentAmount()));
        loan.setOutstanding(money(request.principal()));
        loan.setGrantedOn(request.grantedOn());
        loan.setStatus(EmployeeLoan.Status.ACTIVE);
        EmployeeLoan saved = loans.save(loan);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("EmployeeLoan")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getReference())
                .summary("Granted " + saved.getLoanType() + " of " + saved.getPrincipal()
                        + " to " + employee.getEmployeeCode())
                .module("PAYROLL")
                .succeeded(true)
                .build());
        return toLoanResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<HrDtos.LoanResponse> listLoans(UUID employeeId) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> AppException.notFound("Employee"));
        return loans.findByEmployeeIdOrderByGrantedOnDesc(employee.getId()).stream()
                .map(this::toLoanResponse)
                .toList();
    }

    // ------------------------------------------------------------ payroll runs

    @Transactional(readOnly = true)
    public List<HrDtos.PayrollRunResponse> listRuns(PayrollRun.Status status) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        List<PayrollRun> found = status != null
                ? runs.findByStatusOrderByPeriodYearDescPeriodMonthDesc(status)
                : runs.findAllByOrderByPeriodYearDescPeriodMonthDesc();
        return found.stream().map(this::toRunResponse).toList();
    }

    @Transactional(readOnly = true)
    public HrDtos.PayrollRunResponse getRun(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        return toRunResponse(requireRun(id));
    }

    @Transactional(readOnly = true)
    public List<HrDtos.PayslipResponse> runPayslips(UUID runId) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        requireRun(runId);
        return payslips.findByPayrollRunIdOrderByEmployeeEmployeeCodeAsc(runId).stream()
                .map(this::toPayslipResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public HrDtos.PayslipResponse getPayslip(UUID id) {
        Payslip payslip = payslips.findById(id).orElseThrow(() -> AppException.notFound("Payslip"));
        if (!auth.hasPermission("PAYROLL_READ")) {
            requireSelf(payslip.getEmployee());
        }
        return toPayslipResponse(payslip);
    }

    /** A staff member reading their own payslips, without seeing anyone else's. */
    @Transactional(readOnly = true)
    public List<HrDtos.PayslipResponse> myPayslips() {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYSLIP_READ");
        UUID employeeId = employees.findByUserId(auth.requireUser().userId())
                .map(Employee::getId)
                .orElseThrow(() -> AppException.notFound("Employee"));
        return payslips.findForEmployeeWithItems(employeeId).stream()
                .map(this::toPayslipResponse)
                .toList();
    }

    /**
     * Processing a month.
     *
     * <p>The run is refused if the period was already processed, because a second run would
     * pay everyone twice. Every figure is derived here: overtime from recorded attendance,
     * tax from the bracket table in force for the period, and loan recovery from the
     * outstanding balance. An employee with no published salary structure is a data problem,
     * so it is reported rather than quietly paid nothing.
     */
    @Transactional
    public HrDtos.PayrollRunResponse process(HrDtos.ProcessPayroll request) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_PROCESS");
        int year = request.periodYear();
        int month = request.periodMonth();
        if (runs.existsByPeriodYearAndPeriodMonth(year, month)) {
            throw AppException.duplicate("Payroll for " + year + "-" + String.format("%02d", month)
                    + " has already been processed.");
        }
        YearMonth period = YearMonth.of(year, month);
        LocalDate periodStart = period.atDay(1);
        LocalDate periodEnd = period.atEndOfMonth();

        List<Employee> payable = employees.findPayableBy(periodEnd);
        if (request.employeeIds() != null && !request.employeeIds().isEmpty()) {
            List<UUID> wanted = request.employeeIds();
            payable = payable.stream().filter(employee -> wanted.contains(employee.getId())).toList();
        }
        List<String> unpriced = payable.stream()
                .filter(employee -> employee.getSalaryStructure() == null
                        || !employee.getSalaryStructure().isPublished())
                .map(Employee::getEmployeeCode)
                .toList();
        if (!unpriced.isEmpty()) {
            throw AppException.rule("These employees have no published salary structure and cannot be paid: "
                    + String.join(", ", unpriced) + ".");
        }
        if (payable.isEmpty()) {
            throw AppException.rule("No employees are payable for " + period + ".");
        }

        Map<UUID, BigDecimal> bonuses = request.bonuses() == null ? Map.of() : request.bonuses();
        for (BigDecimal bonus : bonuses.values()) {
            if (bonus.compareTo(BigDecimal.ZERO) < 0) {
                throw AppException.rule("A bonus cannot be negative.");
            }
        }

        PayrollRun run = new PayrollRun();
        run.setPeriodYear(year);
        run.setPeriodMonth(month);
        run.setStatus(PayrollRun.Status.DRAFT);
        run.setNotes(trimToNull(request.notes()));
        PayrollRun savedRun = runs.save(run);

        TaxRule rule = taxRuleFor(period.atEndOfMonth());
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalDeductions = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        for (Employee employee : payable) {
            SalaryStructure structure = salaryStructures.findWithComponentsById(
                            employee.getSalaryStructure().getId())
                    .orElseThrow(() -> AppException.notFound("Salary structure"));
            BigDecimal bonus = bonuses.getOrDefault(employee.getId(), BigDecimal.ZERO);
            if (bonus.compareTo(structure.getBasicSalary()) > 0) {
                // A bonus larger than the monthly salary is almost always a typo, so it is
                // refused rather than silently paid.
                throw AppException.rule("The bonus for " + employee.getEmployeeCode()
                        + " cannot exceed their monthly basic salary of "
                        + structure.getBasicSalary() + ".");
            }
            Computation computed = compute(employee, structure, period, rule, bonus);
            Payslip payslip = new Payslip();
            payslip.setPayrollRun(savedRun);
            payslip.setEmployee(employee);
            payslip.setBasicSalary(computed.basicSalary());
            payslip.setTotalAllowances(computed.allowances());
            payslip.setOvertimeAmount(computed.overtimeAmount());
            payslip.setOvertimeMinutes(computed.overtimeMinutes());
            payslip.setBonus(computed.bonus());
            payslip.setLoanDeduction(computed.loanDeduction());
            payslip.setOtherDeductions(computed.otherDeductions());
            payslip.setTotalDeductions(computed.otherDeductions().add(computed.loanDeduction()));
            payslip.setGrossSalary(computed.gross());
            payslip.setTax(computed.tax());
            payslip.setNetSalary(computed.gross()
                    .subtract(computed.otherDeductions().add(computed.loanDeduction()))
                    .subtract(computed.tax()));
            payslip.setCurrency(structure.getCurrency());
            payslip.setTaxRuleCode(rule == null ? null : rule.getCode());
            payslip.setStatus(Payslip.Status.GENERATED);

            payslip.addItem(line("Basic salary", PayslipItem.ComponentType.ALLOWANCE, computed.basicSalary()));
            for (SalaryComponent component : structure.getComponents()) {
                if (component.getComponentType() != SalaryComponent.ComponentType.ALLOWANCE) {
                    continue;
                }
                BigDecimal amount = component.amountOn(computed.basicSalary());
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    payslip.addItem(line(component.getName(),
                            PayslipItem.ComponentType.ALLOWANCE, amount));
                }
            }
            if (computed.overtimeAmount().compareTo(BigDecimal.ZERO) > 0) {
                payslip.addItem(line("Overtime",
                        PayslipItem.ComponentType.OVERTIME, computed.overtimeAmount()));
            }
            if (computed.bonus().compareTo(BigDecimal.ZERO) > 0) {
                payslip.addItem(line("Bonus", PayslipItem.ComponentType.BONUS, computed.bonus()));
            }
            if (computed.loanDeduction().compareTo(BigDecimal.ZERO) > 0) {
                payslip.addItem(line("Loan and advance recovery",
                        PayslipItem.ComponentType.LOAN, computed.loanDeduction()));
            }
            for (SalaryComponent component : structure.getComponents()) {
                if (component.getComponentType() != SalaryComponent.ComponentType.DEDUCTION) {
                    continue;
                }
                BigDecimal amount = component.amountOn(computed.basicSalary());
                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    payslip.addItem(line(component.getName(),
                            PayslipItem.ComponentType.DEDUCTION, amount));
                }
            }
            if (computed.tax().compareTo(BigDecimal.ZERO) > 0) {
                payslip.addItem(line("Tax", PayslipItem.ComponentType.TAX, computed.tax()));
            }
            // Saving the payslip once cascades to its line items, so a slip and its
            // breakdown can never end up out of step with each other.
            payslips.save(payslip);

            collectLoans(employee, computed.loanDeduction());

            totalGross = totalGross.add(computed.gross());
            totalDeductions = totalDeductions.add(computed.otherDeductions().add(computed.loanDeduction()));
            totalTax = totalTax.add(computed.tax());
            totalNet = totalNet.add(computed.gross()
                    .subtract(computed.otherDeductions().add(computed.loanDeduction()))
                    .subtract(computed.tax()));
        }

        savedRun.setEmployeeCount(payable.size());
        savedRun.setTotalGross(money(totalGross));
        savedRun.setTotalDeductions(money(totalDeductions));
        savedRun.setTotalTax(money(totalTax));
        savedRun.setTotalNet(money(totalNet));
        savedRun.setStatus(PayrollRun.Status.PROCESSED);
        savedRun.setProcessedAt(java.time.Instant.now());
        PayrollRun finished = runs.save(savedRun);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("PayrollRun")
                .entityId(finished.getId().toString())
                .entityLabel(finished.period())
                .summary("Processed payroll for " + payable.size() + " employee(s), gross "
                        + money(totalGross) + " and net " + money(totalNet))
                .module("PAYROLL")
                .succeeded(true)
                .build());
        return toRunResponse(finished);
    }

    /**
     * Approval is separate from processing and carries a different permission, so somebody who
     * prepared a run still cannot sign it off without holding PAYROLL_APPROVE.
     *
     * <p>What this deliberately does <em>not</em> do is refuse an administrator approving a run
     * they prepared. The gate is a permission, not an identity: most schools have one person
     * holding both, and barring them would leave payroll unable to leave PROCESSED at all. The
     * trail an auditor needs is kept instead — {@link PayrollRun#getCreatedBy()} is whoever
     * called {@link #process} and {@link PayrollRun#getApprovedBy()} is whoever signed it, so
     * the two can be compared after the fact rather than the pair being blocked up front.
     */
    @Transactional
    public HrDtos.PayrollRunResponse approve(UUID id, HrDtos.ApprovePayroll request) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_APPROVE");
        PayrollRun run = requireRun(id);
        if (run.getStatus() != PayrollRun.Status.PROCESSED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a processed run can be approved; this one is " + run.getStatus() + ".");
        }
        run.approve(auth.requireUser().userId());
        if (request.notes() != null && !request.notes().isBlank()) {
            run.setNotes(request.notes().trim());
        }
        List<Payslip> approved = payslips.findByPayrollRunIdOrderByEmployeeEmployeeCodeAsc(id);
        approved.forEach(payslip -> payslip.setStatus(Payslip.Status.APPROVED));
        payslips.saveAll(approved);
        audit.record(AuditEvent.builder()
                .action(AuditAction.APPROVE)
                .entityType("PayrollRun")
                .entityId(run.getId().toString())
                .entityLabel(run.period())
                .summary("Approved payroll for " + run.period() + " covering " + run.getEmployeeCount()
                        + " employee(s)")
                .module("PAYROLL")
                .succeeded(true)
                .build());
        return toRunResponse(runs.save(run));
    }



    /**
     * Points the payroll module at this institution's chart of accounts.
     *
     * <p>Every code is checked here rather than at posting time: an account that does not
     * exist, is a heading, or has been deactivated is caught while somebody is looking at
     * the chart, instead of half way through a month-end when payroll is waiting on it.
     */
    @Transactional
    public HrDtos.LedgerAccountsResponse configureLedgerAccounts(HrDtos.LedgerAccountsRequest request) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("SALARY_MANAGE");
        Map<String, String> wanted = new LinkedHashMap<>();
        wanted.put("salaryExpense", request.salaryExpense());
        wanted.put("netPayable", request.netPayable());
        wanted.put("taxPayable", request.taxPayable());
        putIfPresent(wanted, "loanReceivable", request.loanReceivable());
        putIfPresent(wanted, "deductionsPayable", request.deductionsPayable());

        Map<String, String> names = new LinkedHashMap<>();
        wanted.forEach((key, code) -> names.put(key, requirePostableAccount(code.trim()).getName()));

        ModuleSetting setting = moduleSettings.findByModuleKey(ModuleKey.PAYROLL)
                .orElseGet(() -> ModuleSetting.of(ModuleKey.PAYROLL, true));
        Map<String, Object> document = readSettingsDocument(setting);
        document.put(LEDGER_ACCOUNTS_KEY, wanted);
        try {
            setting.setSettings(mapper.writeValueAsString(document));
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The payroll ledger account settings could not be stored.");
        }
        moduleSettings.save(setting);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("ModuleSetting")
                .entityId(setting.getId().toString())
                .entityLabel("PAYROLL")
                .summary("Set payroll ledger accounts to " + wanted)
                .module("PAYROLL")
                .succeeded(true)
                .build());
        return toLedgerAccountsResponse(wanted, names);
    }

    /** The configured accounts, for the screen that shows where payroll will be booked. */
    @Transactional(readOnly = true)
    public HrDtos.LedgerAccountsResponse ledgerAccounts() {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        Map<String, String> wanted = moduleSettings.findByModuleKey(ModuleKey.PAYROLL)
                .map(setting -> readSettingsDocument(setting).get(LEDGER_ACCOUNTS_KEY))
                .filter(Map.class::isInstance)
                .map(node -> (Map<String, String>) node)
                .orElseGet(Map::of);
        Map<String, String> names = new LinkedHashMap<>();
        wanted.forEach((key, code) -> accounts.findByCodeIgnoreCase(code.trim())
                .ifPresent(account -> names.put(key, account.getName())));
        return toLedgerAccountsResponse(wanted, names);
    }

    private void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
    }

    private Map<String, Object> readSettingsDocument(ModuleSetting setting) {
        if (setting.getSettings() == null || setting.getSettings().isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            return mapper.readValue(setting.getSettings(), new TypeReference<>() {
            });
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The payroll module settings are not readable JSON.");
        }
    }

    private ChartOfAccounts requirePostableAccount(String code) {
        ChartOfAccounts account = accounts.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Account " + code + " is not in the chart of accounts."));
        if (!account.isPostable()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Account " + code + " is a heading and cannot be posted to.");
        }
        if (!account.isActive()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Account " + code + " is inactive.");
        }
        return account;
    }

    private HrDtos.LedgerAccountsResponse toLedgerAccountsResponse(Map<String, String> mapping,
            Map<String, String> names) {
        return new HrDtos.LedgerAccountsResponse(
                mapping.get("salaryExpense"), names.get("salaryExpense"),
                mapping.get("netPayable"), names.get("netPayable"),
                mapping.get("taxPayable"), names.get("taxPayable"),
                mapping.get("loanReceivable"), names.get("loanReceivable"),
                mapping.get("deductionsPayable"), names.get("deductionsPayable"));
    }

    /**
     * Books an approved run into the ledger.
     *
     * <p>The entry is the standard salary one: the month's gross salary is debited to the
     * salary expense account and credited to what the institution owes its staff and the
     * authorities -- net pay, tax, any other withheld deductions, and loan recovery, which
     * reduces a receivable rather than crediting a payable. Nothing is paid out here, so no
     * bank leg is created; paying the net pay is a separate treasury step.
     *
     * <p>The accounts are configuration, not code. Each institution points the payroll
     * module at its own chart of accounts through the module's JSON settings, because a
     * school's salary account and its tax payable account are its own business.
     *
     * <p>Booking twice is impossible rather than merely discouraged: the run remembers the
     * entry it produced, and a second call returns that same entry.
     */
    @Transactional
    public HrDtos.LedgerPostingResponse postToLedger(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("ACCOUNTING_POST");
        PayrollRun run = requireRun(id);

        if (run.getJournalEntryId() != null) {
            return toPostingResponse(run, true);
        }
        if (run.getStatus() != PayrollRun.Status.APPROVED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only an approved run can be booked into the ledger; this one is "
                            + run.getStatus() + ".");
        }
        if (run.getEmployeeCount() == 0) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Run " + run.period() + " covers no employees, so there is nothing to book.");
        }

        LocalDate entryDate = YearMonth.of(run.getPeriodYear(), run.getPeriodMonth())
                .atEndOfMonth();
        AccountingPeriod period = accountingPeriods
                .findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
                        entryDate, entryDate)
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "No accounting period covers " + run.period()
                                + ". Open one before booking payroll, or the salary will not "
                                + "appear in the ledger."));

        List<Payslip> slips = payslips.findByPayrollRunIdOrderByEmployeeEmployeeCodeAsc(id);
        Map<String, String> mapping = ledgerAccountMapping();
        List<AccountingDtos.JournalLineRequest> lines = new ArrayList<>();

        // The debit side: what the month of salary cost the institution.
        lines.add(debit(requireAccount(mapping, "salaryExpense"), "Salary expense",
                run.getTotalGross(), run.period()));

        // The credit side: what it now owes, split by what it is owed to.
        lines.add(credit(requireAccount(mapping, "netPayable"), "Net salary payable",
                run.getTotalNet(), run.period()));
        lines.add(credit(requireAccount(mapping, "taxPayable"), "Tax payable",
                run.getTotalTax(), run.period()));

        BigDecimal loanRecovery = slips.stream()
                .map(Payslip::getLoanDeduction)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal otherDeductions = slips.stream()
                .map(Payslip::getOtherDeductions)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        // Both legs are omitted when nothing was withheld of that kind, so a payroll with no
        // loans does not post a zero line to the loan account.
        if (loanRecovery.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(credit(requireAccount(mapping, "loanReceivable"), "Staff loan recovery",
                    loanRecovery, run.period()));
        }
        if (otherDeductions.compareTo(BigDecimal.ZERO) > 0) {
            lines.add(credit(requireAccount(mapping, "deductionsPayable"),
                    "Other deductions payable", otherDeductions, run.period()));
        }
        lines.removeIf(line -> line.credit().compareTo(BigDecimal.ZERO) == 0
                && line.debit().compareTo(BigDecimal.ZERO) == 0);

        AccountingDtos.JournalEntryResponse entry = accounting.createEntry(
                new AccountingDtos.CreateJournalEntry(
                        entryDate,
                        period.getId(),
                        JournalEntry.SourceType.PAYROLL,
                        run.getId(),
                        "Payroll for " + run.period() + " (" + run.getEmployeeCount()
                                + " employees)",
                        lines));
        accounting.postEntry(entry.id());

        run.setJournalEntryId(entry.id());
        runs.save(run);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("JournalEntry")
                .entityId(entry.id().toString())
                .entityLabel(entry.entryNumber())
                .summary("Booked payroll " + run.period() + " for " + run.getTotalGross()
                        + " gross")
                .module("PAYROLL")
                .succeeded(true)
                .build());
        return toPostingResponse(run, false);
    }

    private HrDtos.LedgerPostingResponse toPostingResponse(PayrollRun run, boolean alreadyPosted) {
        JournalEntry entry = accountingEntry(run.getJournalEntryId());
        return new HrDtos.LedgerPostingResponse(
                run.getId(),
                entry.getId(),
                entry.getEntryNumber(),
                entry.getEntryDate(),
                run.getTotalGross(),
                run.getTotalNet(),
                run.getTotalTax(),
                run.getTotalDeductions(),
                alreadyPosted);
    }

    /**
     * The chart of accounts the payroll module posts to, read from the module's own JSON
     * settings. A missing or unconfigured account is a configuration error rather than a
     * silent default, because posting salary to the wrong account is expensive to unpick.
     */
    private Map<String, String> ledgerAccountMapping() {
        String json = moduleSettings.findByModuleKey(ModuleKey.PAYROLL)
                .map(setting -> setting.getSettings())
                .orElse(null);
        if (json == null || json.isBlank()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Set the payroll ledger accounts on the payroll module settings before "
                            + "booking a run. Expected JSON such as "
                            + "{\"salaryExpense\":\"6000\",\"netPayable\":\"2100\","
                            + "\"taxPayable\":\"2200\",\"loanReceivable\":\"1300\","
                            + "\"deductionsPayable\":\"2300\"}.");
        }
        try {
            JsonNode node = mapper.readTree(json).path(LEDGER_ACCOUNTS_KEY);
            if (!node.isObject()) {
                throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "The payroll module settings do not name the ledger accounts to use. "
                                + "Set them before booking a run.");
            }
            Map<String, String> mapping = new LinkedHashMap<>();
            node.fields().forEachRemaining(field -> {
                if (!field.getValue().isNull() && !field.getValue().asText().isBlank()) {
                    mapping.put(field.getKey(), field.getValue().asText());
                }
            });
            return mapping;
        } catch (JsonProcessingException e) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The payroll ledger account settings are not valid JSON.");
        }
    }

    private ChartOfAccounts requireAccount(Map<String, String> mapping, String key) {
        String code = mapping.get(key);
        if (code == null || code.isBlank()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "The payroll ledger accounts do not name a \"" + key + "\" account.");
        }
        return accounts.findByCodeIgnoreCase(code.trim())
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Payroll ledger account " + code.trim() + " is not in the chart of accounts."));
    }

    private AccountingDtos.JournalLineRequest debit(ChartOfAccounts account, String description,
            BigDecimal amount, String period) {
        return new AccountingDtos.JournalLineRequest(account.getId(),
                description + " for " + period, amount, BigDecimal.ZERO, null, null);
    }

    private AccountingDtos.JournalLineRequest credit(ChartOfAccounts account, String description,
            BigDecimal amount, String period) {
        return new AccountingDtos.JournalLineRequest(account.getId(),
                description + " for " + period, BigDecimal.ZERO, amount, null, null);
    }

    private JournalEntry accountingEntry(UUID entryId) {
        return journalEntries.findById(entryId)
                .orElseThrow(() -> new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "The journal entry booked for this run is missing."));
    }

    /**
     * A dry run of one employee's figures for a period, so a payroll officer can check a
     * calculation before committing the month. Nothing is written.
     */
    @Transactional(readOnly = true)
    public HrDtos.TaxBreakdown preview(UUID employeeId, int year, int month) {
        institutions.requireModuleEnabled(ModuleKey.PAYROLL);
        auth.requirePermission("PAYROLL_READ");
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> AppException.notFound("Employee"));
        if (employee.getSalaryStructure() == null
                || !employee.getSalaryStructure().isPublished()) {
            throw AppException.rule("This employee has no published salary structure.");
        }
        YearMonth period = YearMonth.of(year, month);
        SalaryStructure structure = salaryStructures.findWithComponentsById(
                        employee.getSalaryStructure().getId())
                .orElseThrow(() -> AppException.notFound("Salary structure"));
        TaxRule rule = taxRuleFor(period.atEndOfMonth());
        Computation computed = compute(employee, structure, period, rule, BigDecimal.ZERO);
        return new HrDtos.TaxBreakdown(
                rule == null ? null : rule.getCode(),
                computed.annualTaxable(),
                computed.monthlyTaxable(),
                computed.tax(),
                rule == null ? List.of() : rule.brackets(mapper));
    }

    // ------------------------------------------------------------------ helpers

    private record Computation(
            BigDecimal basicSalary,
            BigDecimal allowances,
            BigDecimal overtimeAmount,
            int overtimeMinutes,
            BigDecimal bonus,
            BigDecimal loanDeduction,
            BigDecimal otherDeductions,
            BigDecimal gross,
            BigDecimal monthlyTaxable,
            BigDecimal annualTaxable,
            BigDecimal tax) {
    }

    private Computation compute(Employee employee, SalaryStructure structure, YearMonth period, TaxRule rule,
                                BigDecimal bonus) {
        BigDecimal basic = money(structure.getBasicSalary());
        BigDecimal allowances = BigDecimal.ZERO;
        BigDecimal otherDeductions = BigDecimal.ZERO;
        for (SalaryComponent component : structure.getComponents()) {
            BigDecimal amount = component.amountOn(basic);
            if (component.getComponentType() == SalaryComponent.ComponentType.ALLOWANCE) {
                allowances = allowances.add(amount);
            } else {
                otherDeductions = otherDeductions.add(amount);
            }
        }
        long overtimeMinutes = attendance.sumOvertimeMinutes(employee.getId(),
                period.atDay(1), period.atEndOfMonth());
        BigDecimal overtimeAmount = money(structure.getOvertimeRate()
                .multiply(BigDecimal.valueOf(overtimeMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP));

        BigDecimal loanDeduction = BigDecimal.ZERO;
        List<EmployeeLoan> active = loans.findByEmployeeIdAndStatusOrderByGrantedOnAsc(
                employee.getId(), EmployeeLoan.Status.ACTIVE);
        for (EmployeeLoan loan : active) {
            if (loanDeduction.compareTo(otherDeductions.add(basic)) >= 0) {
                break;
            }
            loanDeduction = loanDeduction.add(loan.deductibleThisRun());
        }
        // A deduction can never exceed what was actually earned.
        BigDecimal cap = basic.add(allowances).add(overtimeAmount).add(bonus);
        if (loanDeduction.compareTo(cap) > 0) {
            loanDeduction = cap;
        }
        if (otherDeductions.compareTo(cap.subtract(loanDeduction)) > 0) {
            otherDeductions = cap.subtract(loanDeduction);
        }

        BigDecimal gross = money(basic.add(allowances).add(overtimeAmount).add(bonus));
        BigDecimal monthlyTaxable = gross.subtract(otherDeductions).max(BigDecimal.ZERO);
        BigDecimal annualTaxable = monthlyTaxable.multiply(MONTHS);
        BigDecimal tax = rule == null ? BigDecimal.ZERO
                : rule.taxOn(annualTaxable, mapper).divide(MONTHS, 2, RoundingMode.HALF_UP);
        return new Computation(basic, money(allowances), overtimeAmount, (int) overtimeMinutes,
                money(bonus), money(loanDeduction), money(otherDeductions),
                gross, money(monthlyTaxable), money(annualTaxable), money(tax));
    }

    /** Loan balances only move here, so the outstanding figure matches the deductions taken. */
    private void collectLoans(Employee employee, BigDecimal deducted) {
        BigDecimal remaining = deducted;
        List<EmployeeLoan> active = loans.findByEmployeeIdAndStatusOrderByGrantedOnAsc(
                employee.getId(), EmployeeLoan.Status.ACTIVE);
        for (EmployeeLoan loan : active) {
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                break;
            }
            BigDecimal take = loan.deductibleThisRun().min(remaining);
            loan.collect(take);
            loans.save(loan);
            remaining = remaining.subtract(take);
        }
    }

    /**
     * The rule in force at the end of the period. Where a new version and an old one both
     * cover the date, the most recently effective one wins.
     */
    private TaxRule taxRuleFor(LocalDate on) {
        List<TaxRule> effective = taxRules.findEffectiveOn(on);
        if (effective.isEmpty()) {
            return null;
        }
        return effective.stream()
                .max(Comparator.comparing(TaxRule::getEffectiveFrom))
                .orElseThrow();
    }

    private PayslipItem line(String name, PayslipItem.ComponentType type, BigDecimal amount) {
        PayslipItem item = new PayslipItem();
        item.setName(name);
        item.setComponentType(type);
        item.setAmount(money(amount));
        return item;
    }

    private String writeBrackets(List<TaxRule.Bracket> brackets) {
        try {
            return mapper.writeValueAsString(brackets);
        } catch (JsonProcessingException ex) {
            throw AppException.rule("The tax brackets could not be stored.");
        }
    }

    private PayrollRun requireRun(UUID id) {
        return runs.findById(id).orElseThrow(() -> AppException.notFound("Payroll run"));
    }

    private void requireSelf(Employee employee) {
        AuthenticatedUser caller = auth.requireUser();
        if (!caller.hasPermission("PAYSLIP_READ")
                || employee.getUser() == null
                || !employee.getUser().getId().equals(caller.userId())) {
            throw AppException.denied("You do not have permission to perform this action.");
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String defaultReference(EmployeeLoan.LoanType type, String employeeCode, LocalDate grantedOn) {
        String prefix = type == EmployeeLoan.LoanType.ADVANCE ? "ADV" : "LN";
        return prefix + "-" + employeeCode + "-" + grantedOn.toString().replace("-", "");
    }

    private HrDtos.SalaryStructureResponse toSalaryStructureResponse(SalaryStructure structure) {
        List<HrDtos.SalaryComponentResponse> components = structure.getComponents().stream()
                .map(component -> new HrDtos.SalaryComponentResponse(
                        component.getId(),
                        component.getName(),
                        component.getComponentType(),
                        component.getValueType(),
                        component.getValue(),
                        component.isTaxable(),
                        component.amountOn(structure.getBasicSalary())))
                .toList();
        return new HrDtos.SalaryStructureResponse(
                structure.getId(),
                structure.getCode(),
                structure.getName(),
                structure.getBasicSalary(),
                structure.getCurrency(),
                structure.getOvertimeRate(),
                structure.getEffectiveFrom(),
                structure.getDescription(),
                structure.getStatus(),
                components);
    }

    private HrDtos.TaxRuleResponse toTaxRuleResponse(TaxRule rule) {
        return new HrDtos.TaxRuleResponse(rule.getId(), rule.getCode(), rule.getName(),
                rule.brackets(mapper), rule.getEffectiveFrom(), rule.getEffectiveTo(),
                rule.getDescription(), rule.getStatus());
    }

    private HrDtos.LoanResponse toLoanResponse(EmployeeLoan loan) {
        return new HrDtos.LoanResponse(
                loan.getId(),
                loan.getEmployee().getId(),
                loan.getEmployee().getEmployeeCode(),
                loan.getLoanType(),
                loan.getReference(),
                loan.getPrincipal(),
                loan.getInstallmentAmount(),
                loan.getOutstanding(),
                loan.getGrantedOn(),
                loan.getStatus());
    }

    private HrDtos.PayrollRunResponse toRunResponse(PayrollRun run) {
        return new HrDtos.PayrollRunResponse(
                run.getId(),
                run.getPeriodYear(),
                run.getPeriodMonth(),
                run.period(),
                run.getStatus(),
                run.getEmployeeCount(),
                run.getTotalGross(),
                run.getTotalDeductions(),
                run.getTotalTax(),
                run.getTotalNet(),
                run.getProcessedAt(),
                run.getApprovedAt(),
                run.getNotes(),
                run.getJournalEntryId());
    }

    private HrDtos.PayslipResponse toPayslipResponse(Payslip payslip) {
        List<HrDtos.PayslipItemResponse> items = payslip.getItems().stream()
                .map(item -> new HrDtos.PayslipItemResponse(item.getId(), item.getName(),
                        item.getComponentType(), item.getAmount(), item.isEarning()))
                .toList();
        PayrollRun run = payslip.getPayrollRun();
        Employee employee = payslip.getEmployee();
        return new HrDtos.PayslipResponse(
                payslip.getId(),
                run.getId(),
                run.period(),
                employee.getId(),
                employee.getEmployeeCode(),
                employee.fullName(),
                payslip.getBasicSalary(),
                payslip.getTotalAllowances(),
                payslip.getOvertimeAmount(),
                payslip.getOvertimeMinutes(),
                payslip.getBonus(),
                payslip.getLoanDeduction(),
                payslip.getOtherDeductions(),
                payslip.getGrossSalary(),
                payslip.getTax(),
                payslip.getTaxRuleCode(),
                payslip.getTotalDeductions(),
                payslip.getNetSalary(),
                payslip.getCurrency(),
                payslip.getStatus(),
                items);
    }
}