package com.educationerp.portal;

import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * What the three portals read.
 *
 * <p>Every response is stated in terms of the person looking at it rather than of the row it
 * came from: a parent sees "your child" and a teacher sees "your class", so the wording in the
 * interface cannot drift away from what the person is actually allowed to see.
 */
public final class PortalDtos {

    private PortalDtos() {
    }

    // ------------------------------------------------------------------ student

    /**
     * The one screen a student wants: who am I, what am I doing today, and what is wrong.
     */
    public record StudentDashboard(
            StudentProfile student,
            List<CourseRow> courses,
            List<TimetableRow> today,
            AttendanceSummary attendance,
            FeeSummary fees,
            List<PublishedResult> recentResults,
            List<NoticeRow> notices,
            long unreadNotifications
    ) {
    }

    /** One of a student's subjects for the year they are enrolled in. */
    public record CourseRow(
            UUID courseOfferingId,
            String code,
            String courseName,
            String teacherName,
            String room,
            Integer totalMarks,
            String section
    ) {
    }

    public record StudentProfile(
            UUID id,
            String studentNumber,
            String fullName,
            String email,
            String phone,
            String status,
            String schoolClass,
            String section,
            String academicYear,
            String rollNumber
    ) {
    }

    /** Fees as the family sees them: one number to pay, and the next date it is wanted by. */
    public record FeeSummary(
            BigDecimal totalBilled,
            BigDecimal totalPaid,
            BigDecimal outstanding,
            LocalDate nextDueDate,
            long unpaidAssessments
    ) {
    }

    public record PublishedResult(
            UUID id,
            String examination,
            String subject,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage,
            String grade,
            String resultStatus,
            Instant publishedAt
    ) {
    }

    // ------------------------------------------------------------------- parent

    /** One line per child; the parent then picks a child for the detail screens. */
    public record ChildSummary(
            UUID studentId,
            String fullName,
            String studentNumber,
            String schoolClass,
            AttendanceSummary attendance,
            FeeSummary fees,
            long unreadNotifications
    ) {
    }

    public record ParentDashboard(
            String guardianName,
            List<ChildSummary> children,
            List<NoticeRow> notices,
            long unreadNotifications
    ) {
    }

    /**
     * Asks which child the detail screens should open on. Defaults to the first child when
     * left out, so a parent with one child never has to choose.
     */
    public record ChildRequest(
            @NotNull UUID studentId
    ) {
    }

    // ------------------------------------------------------------------ teacher

    public record TeacherDashboard(
            UUID employeeId,
            String teacherName,
            List<TimetableRow> today,
            List<AssignedClass> classes,
            List<PendingMarking> pendingMarking,
            List<NoticeRow> notices,
            long unreadNotifications
    ) {
    }

    /** A class the teacher may see, with the numbers that make the list worth reading. */
    public record AssignedClass(
            UUID courseOfferingId,
            String code,
            String courseName,
            String schoolClass,
            String section,
            Integer enrolledStudents,
            String role
    ) {
    }

    /** An examination whose marks this teacher is responsible for and has not finished. */
    public record PendingMarking(
            UUID examSubjectId,
            String examination,
            String subject,
            long studentsAwaitingMarks
    ) {
    }

    // ------------------------------------------------------------------- shared

    public record TimetableRow(
            UUID entryId,
            DayOfWeek dayOfWeek,
            String startTime,
            String endTime,
            String courseCode,
            String courseName,
            String schoolClass,
            String section,
            String room,
            String teacherName
    ) {
    }

    public record AttendanceSummary(
            BigDecimal percentage,
            long present,
            long absent,
            long late,
            long excused,
            long total,
            LocalDate since,
            LocalDate to
    ) {
    }

    public record NoticeRow(
            UUID id,
            String title,
            String body,
            NoticeAudience audience,
            Instant publishedAt,
            Instant expiresAt
    ) {
    }

    /** Mirrors the communication module's notice audiences without leaking that type. */
    public enum NoticeAudience {
        ALL,
        STUDENTS,
        PARENTS,
        TEACHERS,
        STAFF
    }
}