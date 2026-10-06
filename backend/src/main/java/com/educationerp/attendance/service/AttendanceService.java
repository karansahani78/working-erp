package com.educationerp.attendance.service;

import com.educationerp.attendance.AttendanceCorrection;
import com.educationerp.attendance.AttendanceCorrectionRepository;
import com.educationerp.attendance.AttendanceRecord;
import com.educationerp.attendance.AttendanceRecordRepository;
import com.educationerp.attendance.dto.AttendanceDtos;
import com.educationerp.communication.CommunicationEventCode;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.MessageEvent;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.Enums;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Daily and period attendance for course offerings.
 *
 * <p>Registers are recorded as DRAFT and become official on approval, so a teacher can
 * fix mistakes before the register is locked. After approval a change is not a direct
 * update: it needs an {@link AttendanceCorrection} with a reason, which is then approved
 * and applied. That keeps the "attendance correction should be audited" rule from the
 * blueprint intact, including who changed what and when.
 */
@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final com.educationerp.communication.CommunicationService communication;
    private final com.educationerp.student.StudentRepository students;
    private final org.springframework.context.ApplicationEventPublisher events;
    private final AttendanceRecordRepository records;
    private final AttendanceCorrectionRepository corrections;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    /**
     * Records a whole register at once. Re-sending an entry for the same
     * student/date/period updates the existing draft rather than creating a duplicate,
     * which keeps the client simple to retry.
     */
    @Transactional
    public AttendanceDtos.RegisterResponse recordRegister(AttendanceDtos.RegisterRequest request) {
        auth.requirePermission("ATTENDANCE_MARK");
        UUID actor = currentUserId();

        AttendanceRecord.PeriodType periodType = resolvePeriodType(request);
        List<AttendanceDtos.Entry> entries = request.entries();
        for (AttendanceDtos.Entry entry : entries) {
            AttendanceRecord.Status status = parseStatus(entry.status());
            UUID timeSlot = periodType == AttendanceRecord.PeriodType.PERIOD
                    ? request.timeSlotId()
                    : null;
            AttendanceRecord record = records
                    .findByStudentIdAndCourseOfferingIdAndAttendanceDateAndTimeSlotId(
                            entry.studentId(), request.courseOfferingId(), request.attendanceDate(), timeSlot)
                    .orElseGet(() -> newRecord(entry.studentId(), request, periodType, timeSlot));

            if (record.getWorkflowStatus() == AttendanceRecord.WorkflowStatus.APPROVED) {
                throw AppException.rule("Attendance for this period has already been approved. "
                        + "Request a correction instead.");
            }
            record.setStatus(status);
            record.setMinutesLate(validateLateMinutes(status, entry.minutesLate()));
            record.setRemarks(entry.remarks() != null ? entry.remarks() : request.remarks());
            records.save(record);
        }

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("AttendanceRegister")
                .entityId(request.courseOfferingId().toString())
                .entityLabel(request.attendanceDate().toString())
                .summary("Recorded attendance for " + entries.size() + " students")
                .after(Map.of("courseOfferingId", request.courseOfferingId(),
                        "attendanceDate", request.attendanceDate().toString(),
                        "entries", entries.size()))
                .module("ATTENDANCE")
                .succeeded(true)
                .build());

        return registerView(request.courseOfferingId(), request.attendanceDate(), request.timeSlotId());
    }

    /** Marks the whole register as submitted so it can be reviewed and approved. */
    @Transactional
    public AttendanceDtos.RegisterResponse submit(AttendanceDtos.SubmitRequest request,
                                                 UUID courseOfferingId,
                                                 LocalDate attendanceDate,
                                                 UUID timeSlotId) {
        auth.requirePermission("ATTENDANCE_MARK");
        List<AttendanceRecord> register = loadRegister(courseOfferingId, attendanceDate, timeSlotId);
        if (register.isEmpty()) {
            throw AppException.rule("There is no attendance to submit for this period.");
        }
        for (AttendanceRecord record : register) {
            if (record.getWorkflowStatus() == AttendanceRecord.WorkflowStatus.APPROVED) {
                throw AppException.rule("Attendance for this period has already been approved.");
            }
            record.setWorkflowStatus(AttendanceRecord.WorkflowStatus.DRAFT);
        }
        records.saveAll(register);
        if (request != null && request.remarks() != null) {
            register.forEach(r -> r.setRemarks(request.remarks()));
            records.saveAll(register);
        }

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("AttendanceRegister")
                .entityId(courseOfferingId.toString())
                .entityLabel(attendanceDate.toString())
                .summary("Submitted attendance register for approval")
                .module("ATTENDANCE")
                .succeeded(true)
                .build());

        return registerView(courseOfferingId, attendanceDate, timeSlotId);
    }

    /** Approves the register. This is the point at which attendance becomes official. */
    @Transactional
    public AttendanceDtos.RegisterResponse approve(AttendanceDtos.ApproveRequest request,
                                                   UUID courseOfferingId,
                                                   LocalDate attendanceDate,
                                                   UUID timeSlotId) {
        auth.requirePermission("ATTENDANCE_APPROVE");
        List<AttendanceRecord> register = loadRegister(courseOfferingId, attendanceDate, timeSlotId);
        if (register.isEmpty()) {
            throw AppException.rule("There is no attendance to approve for this period.");
        }
        for (AttendanceRecord record : register) {
            if (record.getWorkflowStatus() == AttendanceRecord.WorkflowStatus.APPROVED) {
                throw AppException.rule("Attendance for this period has already been approved.");
            }
            record.setWorkflowStatus(AttendanceRecord.WorkflowStatus.APPROVED);
            if (request != null && request.remarks() != null) {
                record.setRemarks(request.remarks());
            }
        }
        records.saveAll(register);
        notifyAbsences(register, attendanceDate);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("AttendanceRegister")
                .entityId(courseOfferingId.toString())
                .entityLabel(attendanceDate.toString())
                .summary("Approved attendance for " + register.size() + " students")
                .after(Map.of("workflowStatus", AttendanceRecord.WorkflowStatus.APPROVED.name()))
                .module("ATTENDANCE")
                .succeeded(true)
                .build());

        return registerView(courseOfferingId, attendanceDate, timeSlotId);
    }


    /**
     * Tells the student and their guardians about approved absences.
     *
     * <p>This happens on approval rather than on marking. An unapproved register is the
     * teacher's working copy, and a parent should never be told about a day that may still be
     * corrected.
     */
    private void notifyAbsences(List<AttendanceRecord> register, LocalDate attendanceDate) {
        String markedBy = auth.currentUser() == null ? "the school" : auth.currentUser().displayName();
        for (AttendanceRecord record : register) {
            if (record.getStatus() != AttendanceRecord.Status.ABSENT) {
                continue;
            }
            List<UUID> recipients = communication.recipientsForStudent(record.getStudentId(),
                    null);
            if (recipients.isEmpty()) {
                continue;
            }
            Student student = students.findById(record.getStudentId()).orElse(null);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("studentName", student == null ? "Student" : student.displayName());
            variables.put("date", attendanceDate.toString());
            variables.put("markedBy", markedBy);
            events.publishEvent(MessageEvent.of(CommunicationEventCode.ATTENDANCE_MARKED_ABSENT,
                    recipients, variables, "AttendanceRecord", record.getId()));
        }
    }

    @Transactional(readOnly = true)
    public AttendanceDtos.RegisterResponse register(UUID courseOfferingId, LocalDate attendanceDate, UUID timeSlotId) {
        auth.requirePermission("ATTENDANCE_READ");
        return registerView(courseOfferingId, attendanceDate, timeSlotId);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.Response> forStudent(UUID studentId, LocalDate from, LocalDate to) {
        auth.requireStudentAccess(studentId);
        return records.findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(studentId, from, to).stream()
                .map(AttendanceDtos.Response::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.Response> forOffering(UUID courseOfferingId, LocalDate from, LocalDate to) {
        auth.requirePermission("ATTENDANCE_READ");
        return records.findByCourseOfferingIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                        courseOfferingId, from, to).stream()
                .map(AttendanceDtos.Response::from)
                .toList();
    }

    /**
     * Attendance percentage across the student's whole record. Used by Student 360, where
     * there is no date range to choose.
     */
    @Transactional(readOnly = true)
    public AttendanceDtos.SummaryResponse summary(UUID studentId) {
        return summary(studentId, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31));
    }

    /** Attendance percentage report. Excused absences count as present. */
    @Transactional(readOnly = true)
    public AttendanceDtos.SummaryResponse summary(UUID studentId, LocalDate from, LocalDate to) {
        auth.requireStudentAccess(studentId);
        Map<AttendanceRecord.Status, Long> byStatus = new EnumMap<>(AttendanceRecord.Status.class);
        long total = 0;
        for (AttendanceRecordRepository.StatusCount row : records.countByStatus(studentId, from, to)) {
            byStatus.put(row.getStatus(), row.getTotal());
            total += row.getTotal();
        }
        long present = byStatus.getOrDefault(AttendanceRecord.Status.PRESENT, 0L);
        long late = byStatus.getOrDefault(AttendanceRecord.Status.LATE, 0L);
        long excused = byStatus.getOrDefault(AttendanceRecord.Status.EXCUSED, 0L);
        long absent = byStatus.getOrDefault(AttendanceRecord.Status.ABSENT, 0L);

        String percentage = total == 0 ? null
                : BigDecimal.valueOf(present + late + excused)
                        .multiply(BigDecimal.valueOf(100))
                        .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                        .toPlainString();

        return new AttendanceDtos.SummaryResponse(studentId, from, to, total,
                present, absent, late, excused, percentage);
    }

    // ---------- Corrections ----------

    /**
     * Requests a change to an approved record. The record itself is untouched until the
     * request is approved.
     */
    @Transactional
    public AttendanceDtos.CorrectionResponse requestCorrection(UUID attendanceId,
                                                                AttendanceDtos.CorrectionRequest request) {
        auth.requirePermission("ATTENDANCE_CORRECT");
        AttendanceRecord record = requireRecord(attendanceId);
        if (record.getWorkflowStatus() == AttendanceRecord.WorkflowStatus.DRAFT) {
            throw AppException.rule("This attendance record has not been approved yet. "
                    + "Update it directly while it is still a draft.");
        }
        AttendanceRecord.Status newStatus = parseStatus(request.newStatus());
        if (newStatus == record.getStatus()) {
            throw AppException.rule("The requested status is already the current status.");
        }

        AttendanceCorrection correction = new AttendanceCorrection();
        correction.setAttendanceId(record.getId());
        correction.setOldStatus(record.getStatus());
        correction.setNewStatus(newStatus);
        correction.setReason(request.reason().trim());
        correction.setRequestedBy(currentUserId());
        correction.setRequestedAt(Instant.now());
        correction.setStatus(AttendanceCorrection.Status.PENDING);
        corrections.save(correction);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("AttendanceCorrection")
                .entityId(correction.getId().toString())
                .entityLabel(record.getStudentId().toString())
                .summary("Requested attendance correction " + record.getStatus() + " -> " + newStatus)
                .before(Map.of("status", record.getStatus().name()))
                .after(Map.of("status", newStatus.name()))
                .metadata(Map.of("reason", correction.getReason()))
                .module("ATTENDANCE")
                .succeeded(true)
                .build());

        return AttendanceDtos.CorrectionResponse.from(correction);
    }

    /** Approves or rejects a correction. Approval applies the change and audits it. */
    @Transactional
    public AttendanceDtos.CorrectionResponse decideCorrection(UUID correctionId,
                                                              AttendanceDtos.CorrectionDecision decision) {
        auth.requirePermission("ATTENDANCE_APPROVE");
        AttendanceCorrection correction = corrections.findById(correctionId)
                .orElseThrow(() -> AppException.notFound("Attendance correction"));
        if (correction.getStatus() != AttendanceCorrection.Status.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This correction has already been " + correction.getStatus() + ".");
        }

        AttendanceRecord record = requireRecord(correction.getAttendanceId());
        if (correction.getStatus() == AttendanceCorrection.Status.PENDING
                && record.getStatus() != correction.getOldStatus()) {
            throw new AppException(ErrorCode.CONFLICTING_OPERATION,
                    "The attendance record changed since this correction was requested.");
        }

        if (!decision.approved()) {
            correction.setStatus(AttendanceCorrection.Status.REJECTED);
            correction.setApprovedBy(currentUserId());
            correction.setApprovedAt(Instant.now());
            correction.setApprovalNotes(decision.notes());
        } else {
            record.setStatus(correction.getNewStatus());
            record.setWorkflowStatus(AttendanceRecord.WorkflowStatus.APPROVED);
            records.save(record);

            correction.setStatus(AttendanceCorrection.Status.APPLIED);
            correction.setApprovedBy(currentUserId());
            correction.setApprovedAt(Instant.now());
            correction.setAppliedAt(Instant.now());
            correction.setApprovalNotes(decision.notes());

            audit.record(AuditEvent.builder()
                    .action(com.educationerp.audit.AuditAction.UPDATE)
                    .entityType("AttendanceCorrection")
                    .entityId(correction.getId().toString())
                    .entityLabel(record.getStudentId().toString())
                    .summary("Applied approved attendance correction")
                    .before(Map.of("status", correction.getOldStatus().name()))
                    .after(Map.of("status", correction.getNewStatus().name()))
                    .metadata(Map.of("reason", correction.getReason(),
                            "attendanceId", record.getId().toString()))
                    .module("ATTENDANCE")
                    .succeeded(true)
                    .build());
        }
        corrections.save(correction);
        return AttendanceDtos.CorrectionResponse.from(correction);
    }

    @Transactional(readOnly = true)
    public List<AttendanceDtos.CorrectionResponse> correctionsFor(UUID attendanceId) {
        auth.requirePermission("ATTENDANCE_READ");
        return corrections.findByAttendanceIdOrderByRequestedAtDesc(attendanceId).stream()
                .map(AttendanceDtos.CorrectionResponse::from)
                .toList();
    }

    // ---------- helpers ----------

    private AttendanceRecord newRecord(UUID studentId,
                                      AttendanceDtos.RegisterRequest request,
                                      AttendanceRecord.PeriodType periodType,
                                      UUID timeSlot) {
        AttendanceRecord record = new AttendanceRecord();
        record.setStudentId(studentId);
        record.setCourseOfferingId(request.courseOfferingId());
        record.setAttendanceDate(request.attendanceDate());
        record.setTimeSlotId(timeSlot);
        record.setPeriodType(periodType);
        record.setWorkflowStatus(AttendanceRecord.WorkflowStatus.DRAFT);
        return record;
    }

    private AttendanceDtos.RegisterResponse registerView(UUID courseOfferingId, LocalDate date, UUID timeSlotId) {
        List<AttendanceDtos.Response> entries = records
                .findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(courseOfferingId, date)
                .stream()
                .filter(r -> timeSlotId == null || timeSlotId.equals(r.getTimeSlotId()))
                .map(AttendanceDtos.Response::from)
                .toList();
        String workflow = entries.stream()
                .map(AttendanceDtos.Response::workflowStatus)
                .distinct()
                .sorted()
                .reduce((a, b) -> a.equals(b) ? a : "MIXED")
                .orElse(AttendanceRecord.WorkflowStatus.DRAFT.name());
        return new AttendanceDtos.RegisterResponse(courseOfferingId, date, timeSlotId, workflow, entries);
    }

    private List<AttendanceRecord> loadRegister(UUID courseOfferingId, LocalDate date, UUID timeSlotId) {
        return records.findByCourseOfferingIdAndAttendanceDateOrderByStudentIdAsc(courseOfferingId, date).stream()
                .filter(r -> timeSlotId == null || timeSlotId.equals(r.getTimeSlotId()))
                .toList();
    }

    private AttendanceRecord requireRecord(UUID id) {
        return records.findById(id).orElseThrow(() -> AppException.notFound("Attendance record"));
    }

    private AttendanceRecord.PeriodType resolvePeriodType(AttendanceDtos.RegisterRequest request) {
        AttendanceRecord.PeriodType periodType = request.periodType() == null
                ? (request.timeSlotId() == null ? AttendanceRecord.PeriodType.DAILY : AttendanceRecord.PeriodType.PERIOD)
                : Enums.parse(AttendanceRecord.PeriodType.class, request.periodType(), "periodType");
        if (periodType == AttendanceRecord.PeriodType.PERIOD && request.timeSlotId() == null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A time slot is required for period attendance.");
        }
        if (periodType == AttendanceRecord.PeriodType.DAILY && request.timeSlotId() != null) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Daily attendance must not specify a time slot.");
        }
        return periodType;
    }

    private AttendanceRecord.Status parseStatus(String value) {
        return Enums.parse(AttendanceRecord.Status.class, value, "status");
    }

    /** A student who is on time cannot be recorded as late for zero minutes. */
    private Integer validateLateMinutes(AttendanceRecord.Status status, Integer minutesLate) {
        if (status != AttendanceRecord.Status.LATE) {
            return null;
        }
        if (minutesLate == null || minutesLate == 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Minutes late is required when the status is LATE.");
        }
        return minutesLate;
    }

    private UUID currentUserId() {
        AuthenticatedUser user = auth.currentUser();
        return user == null ? null : user.userId();
    }
}