package com.educationerp.attendance;

import com.educationerp.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.util.UUID;

/**
 * One student's attendance for a date and period.
 *
 * <p>Attendance is recorded as a draft and only becomes official once approved, which
 * keeps a teacher from publishing a class register that is still being corrected.
 */
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord extends BaseEntity {

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Column(name = "course_offering_id")
    private UUID courseOfferingId;

    @Column(name = "enrollment_id")
    private UUID enrollmentId;

    @Column(name = "attendance_date", nullable = false)
    private LocalDate attendanceDate;

    @Column(name = "time_slot_id")
    private UUID timeSlotId;

    @Enumerated(EnumType.STRING)
    @Column(name = "period_type", nullable = false, length = 20)
    private PeriodType periodType = PeriodType.PERIOD;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private Status status;

    @Column(name = "minutes_late")
    private Integer minutesLate;

    @Column(name = "recorded_by")
    private UUID recordedBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_workflow", nullable = false, length = 20)
    private WorkflowStatus workflowStatus = WorkflowStatus.DRAFT;

    @Column(name = "remarks", length = 500)
    private String remarks;

    public enum Status {
        PRESENT, ABSENT, LATE, EXCUSED;

        /** Present for credit purposes, including excused absences. */
        public boolean countsAsPresent() {
            return this == PRESENT || this == LATE || this == EXCUSED;
        }
    }

    public enum PeriodType {
        DAILY, PERIOD
    }

    public enum WorkflowStatus {
        DRAFT, APPROVED, REJECTED
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public UUID getCourseOfferingId() {
        return courseOfferingId;
    }

    public void setCourseOfferingId(UUID courseOfferingId) {
        this.courseOfferingId = courseOfferingId;
    }

    public UUID getEnrollmentId() {
        return enrollmentId;
    }

    public void setEnrollmentId(UUID enrollmentId) {
        this.enrollmentId = enrollmentId;
    }

    public LocalDate getAttendanceDate() {
        return attendanceDate;
    }

    public void setAttendanceDate(LocalDate attendanceDate) {
        this.attendanceDate = attendanceDate;
    }

    public UUID getTimeSlotId() {
        return timeSlotId;
    }

    public void setTimeSlotId(UUID timeSlotId) {
        this.timeSlotId = timeSlotId;
    }

    public PeriodType getPeriodType() {
        return periodType;
    }

    public void setPeriodType(PeriodType periodType) {
        this.periodType = periodType;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public Integer getMinutesLate() {
        return minutesLate;
    }

    public void setMinutesLate(Integer minutesLate) {
        this.minutesLate = minutesLate;
    }

    public UUID getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(UUID recordedBy) {
        this.recordedBy = recordedBy;
    }

    public WorkflowStatus getWorkflowStatus() {
        return workflowStatus;
    }

    public void setWorkflowStatus(WorkflowStatus workflowStatus) {
        this.workflowStatus = workflowStatus;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }
}