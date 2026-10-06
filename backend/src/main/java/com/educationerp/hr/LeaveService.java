package com.educationerp.hr;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.communication.CommunicationEventCode;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.MessageEvent;
import com.educationerp.common.error.AppException;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Leave types, applications and balances.
 *
 * <p>The balance is the reason this is more than a status column: an approval draws the
 * days down, and the balance is checked before the approval is accepted rather than after,
 * so two managers approving at the same moment cannot both spend the same day.
 */
@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveTypeRepository leaveTypes;
    private final LeaveRequestRepository requests;
    private final LeaveBalanceRepository balances;
    private final EmployeeRepository employees;
    private final AuthorizationChecker auth;
    private final CommunicationService communication;
    private final ApplicationEventPublisher events;
    private final AuditService audit;
    private final InstitutionService institutions;

    // ---------------------------------------------------------------- leave types

    @Transactional(readOnly = true)
    public List<HrDtos.LeaveTypeResponse> listTypes() {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("LEAVE_READ");
        return leaveTypes.findByActiveTrueOrderByNameAsc().stream().map(this::toTypeResponse).toList();
    }

    @Transactional
    public HrDtos.LeaveTypeResponse createType(HrDtos.CreateLeaveType request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_CREATE");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (leaveTypes.findByCodeIgnoreCase(code).isPresent()) {
            throw AppException.duplicate("A leave type with the code " + code + " already exists.");
        }
        LeaveType type = new LeaveType();
        type.setCode(code);
        type.setName(request.name().trim());
        type.setDaysPerYear(request.daysPerYear() == null ? BigDecimal.ZERO : request.daysPerYear());
        type.setPaid(request.paid());
        type.setDescription(trimToNull(request.description()));
        type.setActive(true);
        return toTypeResponse(leaveTypes.save(type));
    }

    // ----------------------------------------------------------------- balances

    @Transactional(readOnly = true)
    public List<HrDtos.LeaveBalanceResponse> balances(UUID employeeId, int year) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("LEAVE_READ");
        if (employees.findById(employeeId).isEmpty()) {
            throw AppException.notFound("Employee");
        }
        return balances.findByEmployeeIdAndLeaveYearOrderByLeaveYearDesc(employeeId, year).stream()
                .map(this::toBalanceResponse)
                .toList();
    }

    /**
     * Entitlements are opened for a year when they are first needed rather than by a
     * scheduled job, so a leave type created in March still applies to the current year.
     */
    @Transactional
    public List<HrDtos.LeaveBalanceResponse> openEntitlements(UUID employeeId, int year) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("EMPLOYEE_UPDATE");
        if (employees.findById(employeeId).isEmpty()) {
            throw AppException.notFound("Employee");
        }
        for (LeaveType type : leaveTypes.findByActiveTrueOrderByNameAsc()) {
            if (type.getDaysPerYear().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (balances.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employeeId, type.getId(), year).isEmpty()) {
                LeaveBalance balance = new LeaveBalance();
                balance.setEmployee(require(employeeId));
                balance.setLeaveType(type);
                balance.setLeaveYear(year);
                balance.setEntitled(type.getDaysPerYear());
                balance.setUsed(BigDecimal.ZERO);
                balances.save(balance);
            }
        }
        return balances.findByEmployeeIdAndLeaveYearOrderByLeaveYearDesc(employeeId, year).stream()
                .map(this::toBalanceResponse)
                .toList();
    }

    // ------------------------------------------------------------- applications

    @Transactional
    public HrDtos.LeaveRequestResponse apply(HrDtos.ApplyLeave request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("LEAVE_APPLY");
        Employee employee = require(targetEmployee(request.employeeId()));
        LeaveType type = leaveTypes.findById(request.leaveTypeId())
                .orElseThrow(() -> AppException.notFound("Leave type"));
        if (!type.isActive()) {
            throw AppException.rule("The leave type " + type.getName() + " is no longer available.");
        }
        if (request.endDate().isBefore(request.startDate())) {
            throw AppException.rule("The last day of leave cannot be before the first.");
        }
        if (request.startDate().isBefore(employee.getJoinDate())) {
            throw AppException.rule("Leave cannot start before the joining date.");
        }
        if (employee.getExitDate() != null && request.startDate().isAfter(employee.getExitDate())) {
            throw AppException.rule("Leave cannot be booked after the employee has left.");
        }
        if (!requests.findOverlapping(employee.getId(), request.startDate(), request.endDate(), null).isEmpty()) {
            throw AppException.duplicate("This employee already has leave covering those dates.");
        }
        LeaveRequest leave = new LeaveRequest();
        leave.setEmployee(employee);
        leave.setLeaveType(type);
        leave.setStartDate(request.startDate());
        leave.setEndDate(request.endDate());
        leave.setDays(LeaveRequest.daysBetween(request.startDate(), request.endDate()));
        leave.setReason(trimToNull(request.reason()));
        leave.setStatus(LeaveRequest.Status.PENDING);
        LeaveRequest saved = requests.save(leave);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("LeaveRequest")
                .entityId(saved.getId().toString())
                .entityLabel(employee.getEmployeeCode())
                .summary("Applied for " + saved.getDays() + " day(s) " + type.getName()
                        + " from " + saved.getStartDate())
                .module("HR")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<HrDtos.LeaveRequestResponse> listRequests(UUID employeeId, LeaveRequest.Status status) {
        List<LeaveRequest> found;
        if (employeeId != null) {
            Employee employee = require(employeeId);
            if (!auth.hasPermission("LEAVE_READ")) {
                requireSelf(employee);
            }
            found = requests.findByEmployeeIdOrderByStartDateDesc(employeeId);
        } else {
            institutions.requireModuleEnabled(ModuleKey.HR);
            auth.requirePermission("LEAVE_READ");
            found = status != null
                    ? requests.findByStatusOrderByStartDateAsc(status)
                    : requests.findAll();
        }
        if (status != null && employeeId != null) {
            found = found.stream().filter(request -> request.getStatus() == status).toList();
        }
        return found.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public HrDtos.LeaveRequestResponse get(UUID id) {
        LeaveRequest request = requireRequest(id);
        if (!auth.hasPermission("LEAVE_READ")) {
            requireSelf(request.getEmployee());
        }
        return toResponse(request);
    }

    /**
     * Approving or rejecting is the only thing that moves a balance. A decision cannot be
     * taken twice, and cancelling an approved request returns the days to the employee.
     */
    @Transactional
    public HrDtos.LeaveRequestResponse decide(UUID id, HrDtos.DecideLeave request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("LEAVE_APPROVE");
        LeaveRequest leave = requireRequest(id);
        if (leave.getStatus() != LeaveRequest.Status.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This request has already been " + leave.getStatus().name().toLowerCase(Locale.ROOT) + ".");
        }
        if (request.status() != LeaveRequest.Status.APPROVED
                && request.status() != LeaveRequest.Status.REJECTED) {
            throw AppException.rule("A decision must approve or reject the request.");
        }
        if (request.status() == LeaveRequest.Status.APPROVED) {
            LeaveBalance balance = balanceFor(leave);
            if (!balance.canTake(leave.getDays())) {
                throw AppException.rule("Employee " + leave.getEmployee().getEmployeeCode() + " has only "
                        + balance.remaining().stripTrailingZeros().toPlainString()
                        + " day(s) of " + leave.getLeaveType().getName() + " left, but the request needs "
                        + leave.getDays().stripTrailingZeros().toPlainString() + ".");
            }
            balance.take(leave.getDays());
            balances.save(balance);
            leave.getEmployee().setStatus(Employee.Status.ON_LEAVE);
            employees.save(leave.getEmployee());
        }
        leave.setStatus(request.status());
        leave.setDecidedBy(auth.requireUser().userId());
        leave.setDecidedAt(Instant.now());
        leave.setDecisionNotes(trimToNull(request.notes()));
        LeaveRequest saved = requests.save(leave);
        if (saved.getStatus() == LeaveRequest.Status.APPROVED) {
            notifyLeaveApproved(saved);
        }
        audit.record(AuditEvent.builder()
                .action(request.status() == LeaveRequest.Status.APPROVED
                        ? AuditAction.APPROVE : AuditAction.UPDATE)
                .entityType("LeaveRequest")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getEmployee().getEmployeeCode())
                .summary(saved.getStatus() + " leave request of " + saved.getDays() + " day(s) from "
                        + saved.getStartDate())
                .module("HR")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }


    /**
     * Tells the member of staff their leave was approved.
     *
     * <p>Sent only for an approval: somebody who was refused needs no message, and a message
     * saying so would be the wrong tone to open with.
     */
    private void notifyLeaveApproved(LeaveRequest leave) {
        List<UUID> recipients = communication.recipientsForEmployee(leave.getEmployee().getId());
        if (recipients.isEmpty()) {
            return;
        }
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("employeeName", leave.getEmployee().fullName());
        variables.put("leaveType", leave.getLeaveType().getName());
        variables.put("startDate", leave.getStartDate().toString());
        variables.put("endDate", leave.getEndDate().toString());
        variables.put("days", leave.getDays().stripTrailingZeros().toPlainString());
        events.publishEvent(MessageEvent.of(CommunicationEventCode.LEAVE_APPROVED,
                recipients, variables, "LeaveRequest", leave.getId()));
    }

    @Transactional
    public HrDtos.LeaveRequestResponse cancel(UUID id) {
        LeaveRequest leave = requireRequest(id);
        if (!auth.hasPermission("LEAVE_APPROVE")) {
            requireSelf(leave.getEmployee());
        }
        if (leave.getStatus() != LeaveRequest.Status.PENDING
                && leave.getStatus() != LeaveRequest.Status.APPROVED) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A " + leave.getStatus().name().toLowerCase(Locale.ROOT)
                            + " request cannot be cancelled.");
        }
        if (leave.getStatus() == LeaveRequest.Status.APPROVED) {
            LeaveBalance balance = balanceFor(leave);
            balance.setUsed(balance.getUsed().subtract(leave.getDays()).max(BigDecimal.ZERO));
            balances.save(balance);
            restoreActiveStatus(leave.getEmployee());
        }
        leave.setStatus(LeaveRequest.Status.CANCELLED);
        leave.setDecidedBy(auth.requireUser().userId());
        leave.setDecidedAt(Instant.now());
        return toResponse(requests.save(leave));
    }

    // ------------------------------------------------------------------ helpers

    private Employee require(UUID id) {
        return employees.findById(id).orElseThrow(() -> AppException.notFound("Employee"));
    }

    private LeaveRequest requireRequest(UUID id) {
        return requests.findById(id).orElseThrow(() -> AppException.notFound("Leave request"));
    }

    /**
     * Resolves the employee behind the caller's own login. Someone who may read employee
     * records may book for others; anyone else is limited to themselves.
     */
    private UUID ownEmployee() {
        return employees.findByUserId(auth.requireUser().userId())
                .map(Employee::getId)
                .orElseThrow(() -> AppException.notFound("Employee"));
    }

    private UUID targetEmployee(UUID requestedId) {
        if (requestedId != null) {
            if (!auth.hasPermission("EMPLOYEE_READ")) {
                throw AppException.denied("You do not have permission to perform this action.");
            }
            return requestedId;
        }
        return ownEmployee();
    }

    private void requireSelf(Employee employee) {
        if (auth.hasPermission("LEAVE_APPLY")
                && employee.getUser() != null
                && employee.getUser().getId().equals(auth.requireUser().userId())) {
            return;
        }
        throw AppException.denied("You do not have permission to perform this action.");
    }

    /**
     * The balance is looked up by the year the leave falls in, so a request spanning new
     * year is charged to the year it starts.
     */
    private LeaveBalance balanceFor(LeaveRequest leave) {
        return balances.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(
                        leave.getEmployee().getId(), leave.getLeaveType().getId(),
                        leave.getStartDate().getYear())
                .orElseThrow(() -> AppException.rule("No leave balance has been opened for "
                        + leave.getEmployee().getEmployeeCode() + " for "
                        + leave.getLeaveType().getName() + " in " + leave.getStartDate().getYear() + "."));
    }

    /** Cancelling approved leave must not leave the employee stuck on leave for ever. */
    private void restoreActiveStatus(Employee employee) {
        boolean stillOnLeave = requests.findByEmployeeIdOrderByStartDateDesc(employee.getId()).stream()
                .anyMatch(request -> request.getStatus() == LeaveRequest.Status.APPROVED
                        && !request.getEndDate().isBefore(LocalDate.now()));
        if (!stillOnLeave && employee.getStatus() == Employee.Status.ON_LEAVE) {
            employee.setStatus(Employee.Status.ACTIVE);
            employees.save(employee);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private HrDtos.LeaveTypeResponse toTypeResponse(LeaveType type) {
        return new HrDtos.LeaveTypeResponse(type.getId(), type.getCode(), type.getName(),
                type.getDaysPerYear(), type.isPaid(), type.getDescription(), type.isActive());
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

    private HrDtos.LeaveRequestResponse toResponse(LeaveRequest leave) {
        return new HrDtos.LeaveRequestResponse(
                leave.getId(),
                leave.getEmployee().getId(),
                leave.getEmployee().getEmployeeCode(),
                leave.getEmployee().fullName(),
                leave.getLeaveType().getId(),
                leave.getLeaveType().getName(),
                leave.getStartDate(),
                leave.getEndDate(),
                leave.getDays(),
                leave.getReason(),
                leave.getStatus(),
                leave.getDecidedBy(),
                leave.getDecidedAt(),
                leave.getDecisionNotes());
    }
}