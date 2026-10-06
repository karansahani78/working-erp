package com.educationerp.attendance.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.educationerp.attendance.AttendanceCorrection;
import com.educationerp.attendance.AttendanceRecord;

/** DTOs for the attendance module. No entity is ever returned directly. */
public final class AttendanceDtos {

    private AttendanceDtos() {
    }

    // ---------- Recording ----------

    /** One student/period entry inside a register. */
    public record Entry(
            @NotNull UUID studentId,
            @NotBlank String status,
            @PositiveOrZero Integer minutesLate,
            @Size(max = 500) String remarks
    ) {
    }

    /** A register for one date and period: the unit of bulk attendance. */
    public record RegisterRequest(
            @NotNull UUID courseOfferingId,
            @NotNull LocalDate attendanceDate,
            UUID timeSlotId,
            String periodType,
            @Size(max = 500) String remarks,
            @NotEmpty @Valid List<Entry> entries
    ) {
    }

    /** Marks the whole register submitted instead of approved. */
    public record SubmitRequest(@Size(max = 500) String remarks) {
    }

    public record ApproveRequest(@Size(max = 500) String remarks) {
    }

    public record CorrectionRequest(
            @NotBlank String newStatus,
            @NotBlank @Size(max = 1000) String reason,
            @PositiveOrZero Integer minutesLate,
            @Size(max = 500) String remarks
    ) {
    }

    public record CorrectionDecision(
            boolean approved,
            @Size(max = 500) String notes
    ) {
    }

    // ---------- Responses ----------

    public record Response(
            UUID id,
            UUID studentId,
            UUID courseOfferingId,
            UUID enrollmentId,
            LocalDate attendanceDate,
            UUID timeSlotId,
            String periodType,
            String status,
            Integer minutesLate,
            String workflowStatus,
            String remarks,
            UUID recordedBy,
            Instant createdAt,
            Instant updatedAt
    ) {
        public static Response from(AttendanceRecord record) {
            return new Response(record.getId(), record.getStudentId(), record.getCourseOfferingId(),
                    record.getEnrollmentId(), record.getAttendanceDate(), record.getTimeSlotId(),
                    record.getPeriodType().name(), record.getStatus().name(), record.getMinutesLate(),
                    record.getWorkflowStatus().name(), record.getRemarks(), record.getRecordedBy(),
                    record.getCreatedAt(), record.getUpdatedAt());
        }
    }

    public record CorrectionResponse(
            UUID id,
            UUID attendanceId,
            String oldStatus,
            String newStatus,
            String reason,
            String status,
            UUID requestedBy,
            Instant requestedAt,
            UUID approvedBy,
            Instant approvedAt,
            Instant appliedAt,
            String approvalNotes
    ) {
        public static CorrectionResponse from(AttendanceCorrection correction) {
            return new CorrectionResponse(correction.getId(), correction.getAttendanceId(),
                    correction.getOldStatus().name(), correction.getNewStatus().name(), correction.getReason(),
                    correction.getStatus().name(), correction.getRequestedBy(), correction.getRequestedAt(),
                    correction.getApprovedBy(), correction.getApprovedAt(), correction.getAppliedAt(),
                    correction.getApprovalNotes());
        }
    }

    /** Register-level view: what a teacher sees when taking attendance. */
    public record RegisterResponse(
            UUID courseOfferingId,
            LocalDate attendanceDate,
            UUID timeSlotId,
            String workflowStatus,
            List<Response> entries
    ) {
    }

    /** Attendance percentage report for one student over a date range. */
    public record SummaryResponse(
            UUID studentId,
            LocalDate from,
            LocalDate to,
            long totalPeriods,
            long presentPeriods,
            long absentPeriods,
            long latePeriods,
            long excusedPeriods,
            String attendancePercentage
    ) {
    }
}