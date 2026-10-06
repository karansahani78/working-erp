package com.educationerp.hr;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Requests and responses for human resources (blueprint section 33).
 *
 * <p>Salary figures arrive as {@link BigDecimal} and are never taken as doubles, and a
 * payslip response carries both its totals and its line items so a disputed figure can be
 * explained rather than merely asserted.
 */
public final class HrDtos {

    private HrDtos() {
    }

    // -------------------------------------------------------------- designations

    public record CreateDesignation(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 30) String level,
            @Size(max = 500) String description) {
    }

    public record DesignationResponse(
            UUID id,
            String code,
            String name,
            String level,
            String description,
            boolean active) {
    }

    // ----------------------------------------------------------------- employees

    public record CreateEmployee(
            @NotBlank @Size(max = 40) String employeeCode,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @Size(max = 100) String lastName,
            @PastOrPresent LocalDate dateOfBirth,
            @Size(max = 20) String gender,
            @Size(max = 80) String nationality,
            @Size(max = 40) String phone,
            @Email @Size(max = 180) String email,
            @Size(max = 400) String address,
            @Size(max = 400) String photoUrl,
            Employee.EmploymentType employmentType,
            @NotNull LocalDate joinDate,
            UUID departmentId,
            UUID designationId,
            UUID salaryStructureId,
            UUID userId,
            @Size(max = 120) String bankName,
            @Size(max = 60) String bankAccount,
            @Size(max = 60) String taxNumber) {
    }

    public record UpdateEmployee(
            @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @Size(max = 100) String lastName,
            @Size(max = 20) String gender,
            @Size(max = 80) String nationality,
            @Size(max = 40) String phone,
            @Email @Size(max = 180) String email,
            @Size(max = 400) String address,
            UUID departmentId,
            UUID designationId,
            UUID salaryStructureId,
            @Size(max = 120) String bankName,
            @Size(max = 60) String bankAccount,
            @Size(max = 60) String taxNumber) {
    }

    /**
     * Leaving is a status change with its own rules, so it is a separate call rather than a
     * field on the update: it has to be refused when it contradicts the exit date.
     */
    public record TerminateEmployee(
            @NotNull LocalDate exitDate,
            Employee.Status status,
            @Size(max = 300) String reason) {
    }

    public record EmployeeResponse(
            UUID id,
            String employeeCode,
            UUID userId,
            String firstName,
            String middleName,
            String lastName,
            String fullName,
            LocalDate dateOfBirth,
            String gender,
            String nationality,
            String phone,
            String email,
            String address,
            String photoUrl,
            Employee.EmploymentType employmentType,
            LocalDate joinDate,
            LocalDate exitDate,
            Employee.Status status,
            UUID departmentId,
            String departmentName,
            UUID designationId,
            String designationName,
            UUID salaryStructureId,
            String salaryStructureName,
            String bankName,
            String bankAccount,
            String taxNumber) {
    }

    public record EmployeeProfile(
            EmployeeResponse employee,
            List<EmploymentResponse> employments,
            List<EmployeeQualificationResponse> qualifications,
            List<EmployeeDocumentResponse> documents,
            List<LeaveBalanceResponse> leaveBalances,
            List<AttendanceResponse> recentAttendance,
            List<PayslipSummary> recentPayslips) {
    }

    // --------------------------------------------------------------- employments

    public record CreateEmployment(
            @NotNull Employee.EmploymentType employmentType,
            @NotNull LocalDate startDate,
            UUID designationId,
            UUID departmentId,
            UUID salaryStructureId,
            @Size(max = 300) String reason) {
    }

    public record EmploymentResponse(
            UUID id,
            Employee.EmploymentType employmentType,
            UUID designationId,
            String designationName,
            UUID departmentId,
            String departmentName,
            UUID salaryStructureId,
            String salaryStructureName,
            LocalDate startDate,
            LocalDate endDate,
            String reason,
            boolean current) {
    }

    // ----------------------------------------------------------- qualifications

    public record CreateQualification(
            @NotBlank @Size(max = 150) String name,
            @Size(max = 60) String level,
            @Size(max = 500) String description) {
    }

    public record QualificationResponse(
            UUID id,
            String name,
            String level,
            String description,
            boolean active) {
    }

    public record AwardQualification(
            @NotNull UUID qualificationId,
            @Size(max = 150) String institution,
            @Min(1900) @Max(2999) Integer awardedYear,
            @Size(max = 20) String grade) {
    }

    public record EmployeeQualificationResponse(
            UUID id,
            UUID qualificationId,
            String name,
            String level,
            String institution,
            Integer awardedYear,
            String grade) {
    }

    // -------------------------------------------------------------- documents

    public record RegisterDocument(
            @NotBlank @Size(max = 40) String documentType,
            @NotBlank @Size(max = 200) String fileName,
            @NotBlank @Size(max = 400) String storageKey,
            @Size(max = 120) String contentType,
            @Min(0) Long sizeBytes,
            @Size(max = 500) String notes) {
    }

    public record ReviewDocument(
            @NotNull EmployeeDocument.Status status,
            @Size(max = 500) String notes) {
    }

    public record EmployeeDocumentResponse(
            UUID id,
            UUID employeeId,
            String documentType,
            String fileName,
            String storageKey,
            String contentType,
            Long sizeBytes,
            EmployeeDocument.Status status,
            String notes) {
    }

    // -------------------------------------------------------------------- leave

    public record CreateLeaveType(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @DecimalMin("0") BigDecimal daysPerYear,
            boolean paid,
            @Size(max = 500) String description) {
    }

    public record LeaveTypeResponse(
            UUID id,
            String code,
            String name,
            BigDecimal daysPerYear,
            boolean paid,
            String description,
            boolean active) {
    }

    /**
     * Leave may be applied for on an employee's behalf, which is why the employee is a
     * field here and not taken from the caller's own record.
     */
    public record ApplyLeave(
            UUID employeeId,
            @NotNull UUID leaveTypeId,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            @Size(max = 500) String reason) {
    }

    public record DecideLeave(
            @NotNull LeaveRequest.Status status,
            @Size(max = 500) String notes) {
    }

    public record LeaveRequestResponse(
            UUID id,
            UUID employeeId,
            String employeeCode,
            String employeeName,
            UUID leaveTypeId,
            String leaveTypeName,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal days,
            String reason,
            LeaveRequest.Status status,
            UUID decidedBy,
            Instant decidedAt,
            String decisionNotes) {
    }

    public record LeaveBalanceResponse(
            UUID id,
            UUID leaveTypeId,
            String leaveTypeName,
            int leaveYear,
            BigDecimal entitled,
            BigDecimal used,
            BigDecimal remaining) {
    }

    // --------------------------------------------------------------- attendance

    /**
     * Overtime may be sent explicitly; when it is left out it is derived from the clock
     * times, so a payroll run can never depend on someone retyping the hours.
     */
    public record MarkAttendance(
            @NotNull LocalDate attendanceDate,
            @NotNull EmployeeAttendance.Status status,
            LocalTime checkIn,
            LocalTime checkOut,
            @Min(0) Integer overtimeMinutes,
            @Size(max = 300) String remarks) {
    }

    public record BulkAttendance(
            @NotNull LocalDate attendanceDate,
            @NotEmpty @Size(max = 500) List<@Valid AttendanceEntry> entries) {
    }

    public record AttendanceEntry(
            @NotNull UUID employeeId,
            @NotNull EmployeeAttendance.Status status,
            LocalTime checkIn,
            LocalTime checkOut,
            @Min(0) Integer overtimeMinutes,
            @Size(max = 300) String remarks) {
    }

    public record AttendanceResponse(
            UUID id,
            UUID employeeId,
            String employeeCode,
            LocalDate attendanceDate,
            EmployeeAttendance.Status status,
            LocalTime checkIn,
            LocalTime checkOut,
            int overtimeMinutes,
            String remarks) {
    }

    // ------------------------------------------------------ salary and tax rules

    public record SalaryComponentRequest(
            @NotBlank @Size(max = 150) String name,
            @NotNull SalaryComponent.ComponentType componentType,
            SalaryComponent.ValueType valueType,
            @NotNull @DecimalMin("0") BigDecimal value,
            boolean taxable) {
    }

    public record SalaryComponentResponse(
            UUID id,
            String name,
            SalaryComponent.ComponentType componentType,
            SalaryComponent.ValueType valueType,
            BigDecimal value,
            boolean taxable,
            BigDecimal amountOnBasic) {
    }

    public record CreateSalaryStructure(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull @DecimalMin("0") BigDecimal basicSalary,
            @Pattern(regexp = "[A-Z]{3}") String currency,
            @DecimalMin("0") BigDecimal overtimeRate,
            LocalDate effectiveFrom,
            @Size(max = 500) String description,
            @Size(max = 20) List<@Valid SalaryComponentRequest> components) {
    }

    public record SalaryStructureResponse(
            UUID id,
            String code,
            String name,
            BigDecimal basicSalary,
            String currency,
            BigDecimal overtimeRate,
            LocalDate effectiveFrom,
            String description,
            SalaryStructure.Status status,
            List<SalaryComponentResponse> components) {
    }

    /** A bracket of a tax scale: {@code upTo} null means the top band. */
    public record TaxBracketRequest(
            @DecimalMin("0") BigDecimal upTo,
            @NotNull @DecimalMin("0") @DecimalMax("100") BigDecimal rate) {
    }

    public record CreateTaxRule(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @NotNull @Size(min = 1, max = 20) List<@Valid TaxBracketRequest> brackets,
            @NotNull LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Size(max = 500) String description) {
    }

    public record TaxRuleResponse(
            UUID id,
            String code,
            String name,
            List<TaxRule.Bracket> brackets,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            String description,
            TaxRule.Status status) {
    }

    // ------------------------------------------------------- loans and advances

    public record GrantLoan(
            @NotNull UUID employeeId,
            @NotNull EmployeeLoan.LoanType loanType,
            @Size(max = 60) String reference,
            @NotNull @DecimalMin(value = "0.01") BigDecimal principal,
            @NotNull @DecimalMin(value = "0.01") BigDecimal installmentAmount,
            @NotNull LocalDate grantedOn) {
    }

    public record LoanResponse(
            UUID id,
            UUID employeeId,
            String employeeCode,
            EmployeeLoan.LoanType loanType,
            String reference,
            BigDecimal principal,
            BigDecimal installmentAmount,
            BigDecimal outstanding,
            LocalDate grantedOn,
            EmployeeLoan.Status status) {
    }

    // ------------------------------------------------------------------ payroll

    public record ProcessPayroll(
            @NotNull @Min(2000) @Max(2999) Integer periodYear,
            @NotNull @Min(1) @Max(12) Integer periodMonth,
            @Size(max = 20) List<@NotNull UUID> employeeIds,
            @Size(max = 20) Map<@NotNull UUID, @DecimalMin("0.00") BigDecimal> bonuses,
            @Size(max = 500) String notes) {
    }

    public record ApprovePayroll(
            @Size(max = 500) String notes) {
    }

    public record PayrollRunResponse(
            UUID id,
            int periodYear,
            int periodMonth,
            String period,
            PayrollRun.Status status,
            int employeeCount,
            BigDecimal totalGross,
            BigDecimal totalDeductions,
            BigDecimal totalTax,
            BigDecimal totalNet,
            Instant processedAt,
            Instant approvedAt,
            String notes,
            UUID journalEntryId) {
    }

    /**
     * The chart of accounts a payroll run is booked into. Salaries are debited and what the
     * institution owes is credited, so the institution chooses its own account codes rather
     * than being told where salary lives.
     */
    public record LedgerAccountsRequest(
            @NotBlank @Size(max = 20) String salaryExpense,
            @NotBlank @Size(max = 20) String netPayable,
            @NotBlank @Size(max = 20) String taxPayable,
            @Size(max = 20) String loanReceivable,
            @Size(max = 20) String deductionsPayable) {
    }

    /** The configured accounts with their chart-of-accounts names resolved. */
    public record LedgerAccountsResponse(
            String salaryExpense,
            String salaryExpenseName,
            String netPayable,
            String netPayableName,
            String taxPayable,
            String taxPayableName,
            String loanReceivable,
            String loanReceivableName,
            String deductionsPayable,
            String deductionsPayableName) {
    }

    /**
     * What booking a run into the ledger did, or confirmed it had already done. The entry
     * number is what an accountant looks for, so it is returned rather than left behind.
     */
    public record LedgerPostingResponse(
            UUID payrollRunId,
            UUID journalEntryId,
            String entryNumber,
            LocalDate entryDate,
            BigDecimal grossSalary,
            BigDecimal netSalary,
            BigDecimal tax,
            BigDecimal deductions,
            boolean alreadyPosted) {
    }

    public record PayslipItemResponse(
            UUID id,
            String name,
            PayslipItem.ComponentType componentType,
            BigDecimal amount,
            boolean earning) {
    }

    public record PayslipResponse(
            UUID id,
            UUID payrollRunId,
            String period,
            UUID employeeId,
            String employeeCode,
            String employeeName,
            BigDecimal basicSalary,
            BigDecimal totalAllowances,
            BigDecimal overtimeAmount,
            int overtimeMinutes,
            BigDecimal bonus,
            BigDecimal loanDeduction,
            BigDecimal otherDeductions,
            BigDecimal grossSalary,
            BigDecimal tax,
            String taxRuleCode,
            BigDecimal totalDeductions,
            BigDecimal netSalary,
            String currency,
            Payslip.Status status,
            List<PayslipItemResponse> items) {
    }

    /** The payslip as it appears in a list, without its line items. */
    public record PayslipSummary(
            UUID id,
            UUID payrollRunId,
            String period,
            BigDecimal grossSalary,
            BigDecimal totalDeductions,
            BigDecimal tax,
            BigDecimal netSalary,
            String currency,
            Payslip.Status status) {
    }

    /** The taxable income a payroll run derived, exposed so the figure can be checked. */
    public record TaxBreakdown(
            String taxRuleCode,
            BigDecimal annualTaxableIncome,
            BigDecimal monthlyTaxableIncome,
            BigDecimal monthlyTax,
            List<TaxRule.Bracket> brackets) {
    }
}