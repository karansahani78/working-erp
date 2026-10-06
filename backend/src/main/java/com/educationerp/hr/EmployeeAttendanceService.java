package com.educationerp.hr;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Staff attendance, one row per employee per day.
 *
 * <p>Marking is idempotent per day: re-marking a day corrects it rather than adding a second
 * row, because a payroll run totals overtime over this table and a duplicate would quietly
 * pay for the same hours twice.
 */
@Service
@RequiredArgsConstructor
public class EmployeeAttendanceService {

    private final EmployeeAttendanceRepository attendance;
    private final EmployeeRepository employees;
    private final AuthorizationChecker auth;
    private final AuditService audit;
    private final InstitutionService institutions;

    @Transactional
    public HrDtos.AttendanceResponse mark(UUID employeeId, HrDtos.MarkAttendance request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("ATTENDANCE_EMPLOYEE_MARK");
        return apply(employeeId, request.attendanceDate(), request.status(), request.checkIn(),
                request.checkOut(), request.overtimeMinutes(), request.remarks());
    }

    /**
     * Marking a whole department in one call, which is how attendance is normally taken.
     * Every entry is validated first, so a bad row does not leave half the department marked.
     */
    @Transactional
    public List<HrDtos.AttendanceResponse> markBulk(HrDtos.BulkAttendance request) {
        institutions.requireModuleEnabled(ModuleKey.HR);
        auth.requirePermission("ATTENDANCE_EMPLOYEE_MARK");
        for (HrDtos.AttendanceEntry entry : request.entries()) {
            if (entry.status().hasWorked() && entry.checkIn() != null && entry.checkOut() != null
                    && !entry.checkOut().isAfter(entry.checkIn())) {
                throw AppException.rule("The clock-out time must be later than the clock-in time for "
                        + entry.employeeId() + ".");
            }
        }
        return request.entries().stream()
                .map(entry -> apply(entry.employeeId(), request.attendanceDate(), entry.status(),
                        entry.checkIn(), entry.checkOut(), entry.overtimeMinutes(), entry.remarks()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<HrDtos.AttendanceResponse> list(UUID employeeId, LocalDate from, LocalDate to) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> AppException.notFound("Employee"));
        if (!auth.hasPermission("ATTENDANCE_EMPLOYEE_READ")) {
            requireSelf(employee);
        }
        LocalDate start = from == null ? YearMonth.now().atDay(1) : from;
        LocalDate end = to == null ? LocalDate.now() : to;
        if (end.isBefore(start)) {
            throw AppException.rule("The end date cannot be before the start date.");
        }
        return attendance.findByEmployeeIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        employeeId, start, end).stream()
                .map(this::toResponse)
                .toList();
    }

    // ------------------------------------------------------------------ helpers

    private HrDtos.AttendanceResponse apply(UUID employeeId, LocalDate date, EmployeeAttendance.Status status,
                                            java.time.LocalTime checkIn, java.time.LocalTime checkOut,
                                            Integer overtimeMinutes, String remarks) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> AppException.notFound("Employee"));
        if (date.isAfter(LocalDate.now())) {
            throw AppException.rule("Attendance cannot be marked for a future date.");
        }
        if (employee.getJoinDate() != null && date.isBefore(employee.getJoinDate())) {
            throw AppException.rule("Attendance cannot be recorded before " + employee.getEmployeeCode()
                    + " joined on " + employee.getJoinDate() + ".");
        }
        if (employee.getExitDate() != null && date.isAfter(employee.getExitDate())) {
            throw AppException.rule("Attendance cannot be recorded after " + employee.getEmployeeCode()
                    + " left on " + employee.getExitDate() + ".");
        }
        if (status == EmployeeAttendance.Status.WEEK_OFF || status == EmployeeAttendance.Status.HOLIDAY) {
            if (overtimeMinutes != null && overtimeMinutes > 0) {
                throw AppException.rule("Overtime cannot be recorded on a "
                        + status.name().toLowerCase(java.util.Locale.ROOT) + " day.");
            }
        }
        if (overtimeMinutes != null && overtimeMinutes > 24 * 60) {
            throw AppException.rule("Overtime in a day cannot exceed 24 hours.");
        }
        // One row per employee per day: marking again corrects the day rather than
        // duplicating the hours that payroll would then total twice.
        EmployeeAttendance record = attendance.findByEmployeeIdAndAttendanceDate(employeeId, date)
                .orElseGet(() -> {
                    EmployeeAttendance fresh = new EmployeeAttendance();
                    fresh.setEmployee(employee);
                    fresh.setAttendanceDate(date);
                    return fresh;
                });
        record.setStatus(status);
        record.setCheckIn(checkIn);
        record.setCheckOut(checkOut);
        record.setOvertimeMinutes(overtimeMinutes == null ? 0 : overtimeMinutes);
        record.setRemarks(remarks == null || remarks.isBlank() ? null : remarks.trim());
        EmployeeAttendance saved = attendance.save(record);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("EmployeeAttendance")
                .entityId(saved.getId().toString())
                .entityLabel(employee.getEmployeeCode() + " " + date)
                .summary("Marked " + employee.getEmployeeCode() + " as " + status
                        + " on " + date + " with " + saved.effectiveOvertimeMinutes() + " overtime minute(s)")
                .module("HR")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    private void requireSelf(Employee employee) {
        if (employee.getUser() != null
                && employee.getUser().getId().equals(auth.requireUser().userId())) {
            return;
        }
        throw AppException.denied("You do not have permission to perform this action.");
    }

    private HrDtos.AttendanceResponse toResponse(EmployeeAttendance record) {
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
}