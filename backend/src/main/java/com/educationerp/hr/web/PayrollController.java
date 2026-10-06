package com.educationerp.hr.web;

import com.educationerp.common.api.ApiResponse;
import com.educationerp.hr.HrDtos;
import com.educationerp.hr.PayrollRun;
import com.educationerp.hr.PayrollService;
import com.educationerp.hr.SalaryStructure;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hr/payroll")
@Tag(name = "HR - Payroll")
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService service;

    // ------------------------------------------------------- salary structures

    @GetMapping("/salary-structures")
    public ApiResponse<List<HrDtos.SalaryStructureResponse>> salaryStructures(
            @RequestParam(required = false) SalaryStructure.Status status) {
        return ApiResponse.ok(service.listSalaryStructures(status));
    }

    @PostMapping("/salary-structures")
    public ApiResponse<HrDtos.SalaryStructureResponse> createSalaryStructure(
            @Valid @RequestBody HrDtos.CreateSalaryStructure request) {
        return ApiResponse.ok(service.createSalaryStructure(request));
    }

    @PostMapping("/salary-structures/{id}/publish")
    public ApiResponse<HrDtos.SalaryStructureResponse> publishSalaryStructure(@PathVariable UUID id) {
        return ApiResponse.ok(service.publishSalaryStructure(id));
    }

    // -------------------------------------------------------------- tax rules

    @GetMapping("/tax-rules")
    public ApiResponse<List<HrDtos.TaxRuleResponse>> taxRules() {
        return ApiResponse.ok(service.listTaxRules());
    }

    @PostMapping("/tax-rules")
    public ApiResponse<HrDtos.TaxRuleResponse> createTaxRule(@Valid @RequestBody HrDtos.CreateTaxRule request) {
        return ApiResponse.ok(service.createTaxRule(request));
    }

    // ----------------------------------------------------- loans and advances

    @GetMapping("/loans")
    public ApiResponse<List<HrDtos.LoanResponse>> loans(@RequestParam UUID employeeId) {
        return ApiResponse.ok(service.listLoans(employeeId));
    }

    @PostMapping("/loans")
    public ApiResponse<HrDtos.LoanResponse> grantLoan(@Valid @RequestBody HrDtos.GrantLoan request) {
        return ApiResponse.ok(service.grantLoan(request));
    }

    // ------------------------------------------------------------ payroll runs

    @GetMapping("/runs")
    public ApiResponse<List<HrDtos.PayrollRunResponse>> runs(@RequestParam(required = false) PayrollRun.Status status) {
        return ApiResponse.ok(service.listRuns(status));
    }

    @GetMapping("/runs/{id}")
    public ApiResponse<HrDtos.PayrollRunResponse> getRun(@PathVariable UUID id) {
        return ApiResponse.ok(service.getRun(id));
    }

    @PostMapping("/runs")
    public ApiResponse<HrDtos.PayrollRunResponse> process(@Valid @RequestBody HrDtos.ProcessPayroll request) {
        return ApiResponse.ok(service.process(request));
    }

    @PostMapping("/runs/{id}/approve")
    public ApiResponse<HrDtos.PayrollRunResponse> approve(@PathVariable UUID id,
                                                          @Valid @RequestBody HrDtos.ApprovePayroll request) {
        return ApiResponse.ok(service.approve(id, request));
    }

    /** Where payroll will be booked. Configured once and reused every month. */
    @GetMapping("/ledger-accounts")
    public ApiResponse<HrDtos.LedgerAccountsResponse> ledgerAccounts() {
        return ApiResponse.ok(service.ledgerAccounts());
    }

    @PutMapping("/ledger-accounts")
    public ApiResponse<HrDtos.LedgerAccountsResponse> configureLedgerAccounts(
            @Valid @RequestBody HrDtos.LedgerAccountsRequest request) {
        return ApiResponse.ok(service.configureLedgerAccounts(request));
    }

    /**
     * Books the run into the ledger. Kept apart from approval on purpose: signing off the
     * figures and moving them into the books are different acts with different permissions.
     */
    @PostMapping("/runs/{id}/post-to-ledger")
    public ApiResponse<HrDtos.LedgerPostingResponse> postToLedger(@PathVariable UUID id) {
        return ApiResponse.ok(service.postToLedger(id));
    }

    @GetMapping("/runs/{id}/payslips")
    public ApiResponse<List<HrDtos.PayslipResponse>> runPayslips(@PathVariable UUID id) {
        return ApiResponse.ok(service.runPayslips(id));
    }

    @GetMapping("/payslips/{id}")
    public ApiResponse<HrDtos.PayslipResponse> getPayslip(@PathVariable UUID id) {
        return ApiResponse.ok(service.getPayslip(id));
    }

    @GetMapping("/payslips/mine")
    public ApiResponse<List<HrDtos.PayslipResponse>> myPayslips() {
        return ApiResponse.ok(service.myPayslips());
    }

    /** A dry run of one employee's figures, so a month can be checked before it is processed. */
    @GetMapping("/preview")
    public ApiResponse<HrDtos.TaxBreakdown> preview(@RequestParam UUID employeeId,
                                                    @RequestParam int year,
                                                    @RequestParam int month) {
        return ApiResponse.ok(service.preview(employeeId, year, month));
    }
}