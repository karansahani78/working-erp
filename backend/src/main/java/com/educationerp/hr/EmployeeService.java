package com.educationerp.hr;

import com.educationerp.academic.Department;
import com.educationerp.academic.DepartmentRepository;
import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.common.error.AppException;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.Year;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Employee records, employment history, qualifications and documents.
 *
 * <p>Employee codes are the identity used on payslips and letters, so they are matched
 * case-insensitively and never silently reused: an exited member of staff keeps their code
 * on the record rather than freeing it for someone else.
 */
@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employees;
    private final EmploymentRepository employments;
    private final DesignationRepository designations;
    private final SalaryStructureRepository salaryStructures;
    private final DepartmentRepository departments;
    private final QualificationRepository qualifications;
    private final EmployeeQualificationRepository employeeQualifications;
    private final EmployeeDocumentRepository documents;
    private final LeaveBalanceRepository leaveBalances;
    private final EmployeeAttendanceRepository attendance;
    private final PayslipRepository payslips;
    private final UserRepository users;
    private final AuthorizationChecker auth;
    private final AuditService audit;
    private final InstitutionService institutions;

    @Transactional(readOnly = true)
    public List<HrDtos.EmployeeResponse> list(UUID departmentId, Employee.Status status) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_READ");
        List<Employee> found;
        if (departmentId != null && status != null) {
            requireDepartment(departmentId);
            found = employees.findByDepartmentIdAndStatusOrderByEmployeeCodeAsc(departmentId, status);
        } else if (departmentId != null) {
            requireDepartment(departmentId);
            found = employees.findByDepartmentIdOrderByEmployeeCodeAsc(departmentId);
        } else if (status != null) {
            found = employees.findByStatusOrderByEmployeeCodeAsc(status);
        } else {
            found = employees.findAll(Sort.by("employeeCode"));
        }
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HrDtos.EmployeeResponse get(UUID id) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_READ");
        return toResponse(require(id));
    }

    /**
     * A caller reading their own record is not an HR administrator, so self-service reads
     * are allowed through {@code PAYSLIP_READ} and resolved by the login on the employee
     * record rather than by any id the client sends.
     */
    @Transactional(readOnly = true)
    public HrDtos.EmployeeProfile profile(UUID id) {
        Employee employee = require(id);
        requireSelfOrPermission(employee, "EMPLOYEE_READ");
        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        return new HrDtos.EmployeeProfile(
                toResponse(employee),
                employments.findByEmployeeIdOrderByStartDateDesc(id).stream().map(this::toEmploymentResponse).toList(),
                employeeQualifications.findByEmployeeIdOrderByIdAsc(id).stream()
                        .map(this::toQualificationResponse).toList(),
                documents.findByEmployeeIdOrderByIdDesc(id).stream().map(this::toDocumentResponse).toList(),
                leaveBalances.findByEmployeeIdAndLeaveYearOrderByLeaveYearDesc(id, Year.now().getValue()).stream()
                        .map(this::toBalanceResponse).toList(),
                attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                                id, monthStart, LocalDate.now()).stream()
                        .map(this::toAttendanceResponse).toList(),
                payslips.findByEmployeeIdOrderByPayrollRunPeriodYearDescPayrollRunPeriodMonthDesc(id).stream()
                        .limit(6)
                        .map(this::toPayslipSummary)
                        .toList());
    }

    @Transactional
    public HrDtos.EmployeeResponse create(HrDtos.CreateEmployee request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_CREATE");
        String code = normaliseCode(request.employeeCode());
        if (employees.existsByEmployeeCodeIgnoreCase(code)) {
            throw AppException.duplicate("An employee with the code " + code + " already exists.");
        }
        User user = request.userId() == null ? null
                : users.findById(request.userId()).orElseThrow(() -> AppException.notFound("User"));
        if (user != null && employees.findByUserId(user.getId()).isPresent()) {
            throw AppException.duplicate("That login is already linked to an employee.");
        }
        Employee.EmploymentType type = request.employmentType() == null
                ? Employee.EmploymentType.FULL_TIME
                : request.employmentType();

        Employee employee = new Employee();
        employee.setEmployeeCode(code);
        employee.setUser(user);
        employee.setFirstName(request.firstName().trim());
        employee.setMiddleName(trimToNull(request.middleName()));
        employee.setLastName(trimToNull(request.lastName()));
        employee.setDateOfBirth(request.dateOfBirth());
        employee.setGender(trimToNull(request.gender()));
        employee.setNationality(trimToNull(request.nationality()));
        employee.setPhone(trimToNull(request.phone()));
        employee.setEmail(trimToNull(request.email()));
        employee.setAddress(trimToNull(request.address()));
        employee.setPhotoUrl(trimToNull(request.photoUrl()));
        employee.setEmploymentType(type);
        employee.setJoinDate(request.joinDate());
        employee.setStatus(Employee.Status.ACTIVE);
        employee.setDepartment(department(request.departmentId()));
        employee.setDesignation(designation(request.designationId()));
        employee.setSalaryStructure(salaryStructure(request.salaryStructureId()));
        employee.setBankName(trimToNull(request.bankName()));
        employee.setBankAccount(trimToNull(request.bankAccount()));
        employee.setTaxNumber(trimToNull(request.taxNumber()));
        Employee saved = employees.save(employee);
        recordEmployment(saved, type, request.designationId(), request.departmentId(),
                request.salaryStructureId(), request.joinDate(), "Initial appointment");
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Employee")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getEmployeeCode())
                .summary("Created employee " + saved.getEmployeeCode() + " (" + saved.fullName() + ")")
                .module("HR")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    @Transactional
    public HrDtos.EmployeeResponse update(UUID id, HrDtos.UpdateEmployee request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        Employee employee = require(id);
        if (request.firstName() != null) {
            employee.setFirstName(request.firstName().trim());
        }
        employee.setMiddleName(trimToNull(request.middleName()));
        employee.setLastName(trimToNull(request.lastName()));
        employee.setGender(trimToNull(request.gender()));
        employee.setNationality(trimToNull(request.nationality()));
        employee.setPhone(trimToNull(request.phone()));
        employee.setEmail(trimToNull(request.email()));
        employee.setAddress(trimToNull(request.address()));
        if (request.departmentId() != null) {
            employee.setDepartment(department(request.departmentId()));
        }
        if (request.designationId() != null) {
            employee.setDesignation(designation(request.designationId()));
        }
        if (request.salaryStructureId() != null) {
            employee.setSalaryStructure(salaryStructure(request.salaryStructureId()));
        }
        employee.setBankName(trimToNull(request.bankName()));
        employee.setBankAccount(trimToNull(request.bankAccount()));
        employee.setTaxNumber(trimToNull(request.taxNumber()));
        Employee saved = employees.save(employee);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Employee")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getEmployeeCode())
                .summary("Updated employee " + saved.getEmployeeCode())
                .module("HR")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Closing an employment: the exit date is stamped and the status set. This is a state
     * change rather than an edit, so it is refused once the person has already left.
     */
    @Transactional
    public HrDtos.EmployeeResponse terminate(UUID id, HrDtos.TerminateEmployee request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        Employee employee = require(id);
        if (employee.getExitDate() != null) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This employee already left on " + employee.getExitDate() + ".");
        }
        if (request.exitDate().isBefore(employee.getJoinDate())) {
            throw AppException.rule("The exit date cannot be earlier than the joining date.");
        }
        employee.setExitDate(request.exitDate());
        employee.setStatus(request.status() == null ? Employee.Status.RESIGNED : request.status());
        closeCurrentEmployment(employee, request.exitDate(), request.reason());
        Employee saved = employees.save(employee);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Employee")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getEmployeeCode())
                .summary("Closed employment for " + saved.getEmployeeCode() + " on " + request.exitDate())
                .module("HR")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    // --------------------------------------------------------------- employments

    @Transactional
    public HrDtos.EmploymentResponse addEmployment(UUID id, HrDtos.CreateEmployment request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        Employee employee = require(id);
        // A new spell may only start once the previous one has closed, otherwise the history
        // would overlap and a payslip could not say which terms applied.
        boolean hasOpenSpell = employments.findByEmployeeIdOrderByStartDateDesc(id).stream()
                .anyMatch(employment -> employment.getEndDate() == null);
        if (hasOpenSpell) {
            throw AppException.rule("Employee " + employee.getEmployeeCode()
                    + " already has an open employment that must be closed first.");
        }
        if (request.startDate().isBefore(employee.getJoinDate())) {
            throw AppException.rule("The employment cannot start before the joining date.");
        }
        Employment employment = recordEmployment(employee, request.employmentType(), request.designationId(),
                request.departmentId(), request.salaryStructureId(), request.startDate(), request.reason());
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Employment")
                .entityId(employment.getId().toString())
                .entityLabel(employee.getEmployeeCode())
                .summary("Recorded a new employment for " + employee.getEmployeeCode()
                        + " from " + request.startDate())
                .module("HR")
                .succeeded(true)
                .build());
        return toEmploymentResponse(employment);
    }

    // ------------------------------------------------------------ qualifications

    @Transactional(readOnly = true)
    public List<HrDtos.QualificationResponse> listQualifications() {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_READ");
        return qualifications.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toQualificationResponse).toList();
    }

    @Transactional
    public HrDtos.QualificationResponse createQualification(HrDtos.CreateQualification request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        String name = request.name().trim();
        if (qualifications.findByNameIgnoreCase(name).isPresent()) {
            throw AppException.duplicate("A qualification named " + name + " already exists.");
        }
        Qualification qualification = new Qualification();
        qualification.setName(name);
        qualification.setLevel(trimToNull(request.level()));
        qualification.setDescription(trimToNull(request.description()));
        qualification.setActive(true);
        return toQualificationResponse(qualifications.save(qualification));
    }

    @Transactional(readOnly = true)
    public List<HrDtos.EmployeeQualificationResponse> listEmployeeQualifications(UUID id) {
        Employee employee = require(id);
        requireSelfOrPermission(employee, "EMPLOYEE_READ");
        return employeeQualifications.findByEmployeeIdOrderByIdAsc(id).stream()
                .map(this::toQualificationResponse).toList();
    }

    @Transactional
    public HrDtos.EmployeeQualificationResponse awardQualification(UUID id, HrDtos.AwardQualification request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        Employee employee = require(id);
        Qualification qualification = qualifications.findById(request.qualificationId())
                .orElseThrow(() -> AppException.notFound("Qualification"));
        if (employeeQualifications.existsByEmployeeIdAndQualificationId(id, request.qualificationId())) {
            throw AppException.duplicate(employee.getEmployeeCode()
                    + " already holds the qualification " + qualification.getName() + ".");
        }
        EmployeeQualification held = new EmployeeQualification();
        held.setEmployee(employee);
        held.setQualification(qualification);
        held.setInstitution(trimToNull(request.institution()));
        held.setAwardedYear(request.awardedYear());
        held.setGrade(trimToNull(request.grade()));
        return toQualificationResponse(employeeQualifications.save(held));
    }

    // ---------------------------------------------------------------- documents

    @Transactional(readOnly = true)
    public List<HrDtos.EmployeeDocumentResponse> listDocuments(UUID id) {
        Employee employee = require(id);
        requireSelfOrPermission(employee, "EMPLOYEE_READ");
        return documents.findByEmployeeIdOrderByIdDesc(id).stream().map(this::toDocumentResponse).toList();
    }

    @Transactional
    public HrDtos.EmployeeDocumentResponse registerDocument(UUID id, HrDtos.RegisterDocument request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        Employee employee = require(id);
        EmployeeDocument document = new EmployeeDocument();
        document.setEmployee(employee);
        document.setDocumentType(request.documentType().trim().toUpperCase(Locale.ROOT));
        document.setFileName(request.fileName().trim());
        document.setStorageKey(request.storageKey().trim());
        document.setContentType(trimToNull(request.contentType()));
        document.setSizeBytes(request.sizeBytes());
        document.setNotes(trimToNull(request.notes()));
        document.setStatus(EmployeeDocument.Status.PENDING);
        return toDocumentResponse(documents.save(document));
    }

    @Transactional
    public HrDtos.EmployeeDocumentResponse reviewDocument(UUID id, UUID documentId,
                                                           HrDtos.ReviewDocument request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        EmployeeDocument document = documents.findById(documentId)
                .orElseThrow(() -> AppException.notFound("Employee document"));
        if (!document.getEmployee().getId().equals(id)) {
            throw AppException.notFound("Employee document");
        }
        if (document.getStatus() != EmployeeDocument.Status.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This document has already been " + document.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        document.setStatus(request.status());
        document.setNotes(trimToNull(request.notes()));
        return toDocumentResponse(documents.save(document));
    }

    // --------------------------------------------------------------- designations

    @Transactional(readOnly = true)
    public List<HrDtos.DesignationResponse> listDesignations() {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_READ");
        return designations.findByActiveTrueOrderByNameAsc().stream()
                .map(this::toDesignationResponse)
                .toList();
    }

    @Transactional
    public HrDtos.DesignationResponse createDesignation(HrDtos.CreateDesignation request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_CREATE");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (designations.findByCodeIgnoreCase(code).isPresent()) {
            throw AppException.duplicate("A designation with the code " + code + " already exists.");
        }
        Designation designation = new Designation();
        designation.setCode(code);
        designation.setName(request.name().trim());
        designation.setLevel(trimToNull(request.level()));
        designation.setDescription(trimToNull(request.description()));
        designation.setActive(true);
        return toDesignationResponse(designations.save(designation));
    }

    // ------------------------------------------------------------------ helpers

    private Employee require(UUID id) {
        return employees.findById(id).orElseThrow(() -> AppException.notFound("Employee"));
    }

    private void requireDepartment(UUID id) {
        departments.findById(id).orElseThrow(() -> AppException.notFound("Department"));
    }

    /**
     * Permission is checked against the employee record's own login, never against an id
     * supplied by the caller, so one employee cannot read another by guessing a UUID.
     */
    private void requireSelfOrPermission(Employee employee, String permission) {
        if (auth.hasPermission(permission)) {
            return;
        }
        AuthenticatedUser caller = auth.requireUser();
        if (caller.hasPermission("PAYSLIP_READ")
                && employee.getUser() != null
                && employee.getUser().getId().equals(caller.userId())) {
            return;
        }
        throw AppException.denied("You do not have permission to perform this action.");
    }

    private Department department(UUID id) {
        return id == null ? null : departments.findById(id).orElseThrow(() -> AppException.notFound("Department"));
    }

    private Designation designation(UUID id) {
        return id == null ? null : designations.findById(id).orElseThrow(() -> AppException.notFound("Designation"));
    }

    private SalaryStructure salaryStructure(UUID id) {
        return id == null ? null
                : salaryStructures.findById(id).orElseThrow(() -> AppException.notFound("Salary structure"));
    }

    private Employment recordEmployment(Employee employee, Employee.EmploymentType type, UUID designationId,
                                        UUID departmentId, UUID salaryStructureId,
                                        LocalDate start, String reason) {
        Employment employment = new Employment();
        employment.setEmployee(employee);
        employment.setEmploymentType(type == null ? Employee.EmploymentType.FULL_TIME : type);
        employment.setDesignation(designation(designationId));
        employment.setDepartment(department(departmentId));
        employment.setSalaryStructure(salaryStructure(salaryStructureId));
        employment.setStartDate(start);
        employment.setReason(trimToNull(reason));
        return employments.save(employment);
    }

    private void closeCurrentEmployment(Employee employee, LocalDate endDate, String reason) {
        employments.findByEmployeeIdOrderByStartDateDesc(employee.getId()).stream()
                .filter(employment -> employment.getEndDate() == null)
                .findFirst()
                .ifPresent(current -> {
                    current.setEndDate(endDate);
                    current.setReason(trimToNull(reason));
                    employments.save(current);
                });
    }

    private String normaliseCode(String code) {
        return code.trim().toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private HrDtos.EmployeeResponse toResponse(Employee employee) {
        return new HrDtos.EmployeeResponse(
                employee.getId(),
                employee.getEmployeeCode(),
                employee.getUser() == null ? null : employee.getUser().getId(),
                employee.getFirstName(),
                employee.getMiddleName(),
                employee.getLastName(),
                employee.fullName(),
                employee.getDateOfBirth(),
                employee.getGender(),
                employee.getNationality(),
                employee.getPhone(),
                employee.getEmail(),
                employee.getAddress(),
                employee.getPhotoUrl(),
                employee.getEmploymentType(),
                employee.getJoinDate(),
                employee.getExitDate(),
                employee.getStatus(),
                employee.getDepartment() == null ? null : employee.getDepartment().getId(),
                employee.getDepartment() == null ? null : employee.getDepartment().getName(),
                employee.getDesignation() == null ? null : employee.getDesignation().getId(),
                employee.getDesignation() == null ? null : employee.getDesignation().getName(),
                employee.getSalaryStructure() == null ? null : employee.getSalaryStructure().getId(),
                employee.getSalaryStructure() == null ? null : employee.getSalaryStructure().getName(),
                employee.getBankName(),
                employee.getBankAccount(),
                employee.getTaxNumber());
    }

    private HrDtos.EmploymentResponse toEmploymentResponse(Employment employment) {
        boolean current = employment.getEndDate() == null || !employment.getEndDate().isBefore(LocalDate.now());
        return new HrDtos.EmploymentResponse(
                employment.getId(),
                employment.getEmploymentType(),
                employment.getDesignation() == null ? null : employment.getDesignation().getId(),
                employment.getDesignation() == null ? null : employment.getDesignation().getName(),
                employment.getDepartment() == null ? null : employment.getDepartment().getId(),
                employment.getDepartment() == null ? null : employment.getDepartment().getName(),
                employment.getSalaryStructure() == null ? null : employment.getSalaryStructure().getId(),
                employment.getSalaryStructure() == null ? null : employment.getSalaryStructure().getName(),
                employment.getStartDate(),
                employment.getEndDate(),
                employment.getReason(),
                current);
    }

    private HrDtos.DesignationResponse toDesignationResponse(Designation designation) {
        return new HrDtos.DesignationResponse(designation.getId(), designation.getCode(), designation.getName(),
                designation.getLevel(), designation.getDescription(), designation.isActive());
    }

    private HrDtos.QualificationResponse toQualificationResponse(Qualification qualification) {
        return new HrDtos.QualificationResponse(qualification.getId(), qualification.getName(),
                qualification.getLevel(), qualification.getDescription(), qualification.isActive());
    }

    private HrDtos.EmployeeQualificationResponse toQualificationResponse(EmployeeQualification held) {
        Qualification qualification = held.getQualification();
        return new HrDtos.EmployeeQualificationResponse(
                held.getId(),
                qualification.getId(),
                qualification.getName(),
                qualification.getLevel(),
                held.getInstitution(),
                held.getAwardedYear(),
                held.getGrade());
    }

    private HrDtos.EmployeeDocumentResponse toDocumentResponse(EmployeeDocument document) {
        return new HrDtos.EmployeeDocumentResponse(
                document.getId(),
                document.getEmployee().getId(),
                document.getDocumentType(),
                document.getFileName(),
                document.getStorageKey(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus(),
                document.getNotes());
    }

    private HrDtos.LeaveBalanceResponse toBalanceResponse(LeaveBalance balance) {
        return new HrDtos.LeaveBalanceResponse(
                balance.getId(),
                balance.getLeaveType().getId(),
                balance.getLeaveType().getName(),
                balance.getLeaveYear(),
                balance.getEntitled(),
                balance.getUsed(),
                balance.remaining());
    }

    private HrDtos.AttendanceResponse toAttendanceResponse(EmployeeAttendance record) {
        return new HrDtos.AttendanceResponse(
                record.getId(),
                record.getEmployee().getId(),
                record.getEmployee().getEmployeeCode(),
                record.getAttendanceDate(),
                record.getStatus(),
                record.getCheckIn(),
                record.getCheckOut(),
                record.effectiveOvertimeMinutes(),
                record.getRemarks());
    }

    private HrDtos.PayslipSummary toPayslipSummary(Payslip payslip) {
        return new HrDtos.PayslipSummary(
                payslip.getId(),
                payslip.getPayrollRun().getId(),
                payslip.getPayrollRun().period(),
                payslip.getGrossSalary(),
                payslip.getTotalDeductions(),
                payslip.getTax(),
                payslip.getNetSalary(),
                payslip.getCurrency(),
                payslip.getStatus());
    }
}