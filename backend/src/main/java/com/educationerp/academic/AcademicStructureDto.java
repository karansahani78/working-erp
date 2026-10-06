package com.educationerp.academic;

import com.educationerp.institution.AcademicModel;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Requests and responses for the academic structure the blueprint defines in sections 13-18
 * and 82: programmes and their versions, curricula, semesters, sections, rooms, course
 * offerings and the timetable.
 *
 * <p>An offering response carries the academic model with it. A school offering is described
 * by its class and section, a college offering by its programme and semester, and the
 * blueprint is explicit that the UI must not be shown both at once; making the model part of
 * the payload lets the client render the right fields without a second lookup.
 */
public final class AcademicStructureDto {

    private AcademicStructureDto() {
    }

    // ------------------------------------------------------------------ programmes

    public record CreateProgram(
            @NotBlank @Size(max = 40) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 40) String level,
            @Min(1) Integer durationSemesters,
            @Min(1) Integer durationYears,
            UUID departmentId) {
    }

    public record ProgramResponse(
            UUID id,
            String code,
            String name,
            String level,
            Integer durationSemesters,
            Integer durationYears,
            UUID departmentId,
            String departmentName,
            Program.Status status) {
    }

    // ----------------------------------------------------------- program versions

    public record CreateProgramVersion(
            @NotNull UUID programId,
            @NotBlank @Size(max = 60) String label,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            @Min(0) Integer totalCredits) {
    }

    public record ProgramVersionResponse(
            UUID id,
            UUID programId,
            String programCode,
            String label,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            Integer totalCredits,
            ProgramVersion.Status status) {
    }

    // ----------------------------------------------------------------- curriculum

    public record CreateCurriculum(
            @NotNull UUID programVersionId,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 500) String description,
            @Min(0) Integer totalCredits,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public record CurriculumResponse(
            UUID id,
            UUID programVersionId,
            String programVersionLabel,
            String name,
            String description,
            Integer totalCredits,
            Integer placedCredits,
            boolean complete,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active) {
    }

    public record CurriculumCourseRequest(
            @NotNull UUID courseId,
            UUID semesterId,
            CurriculumCourse.RequirementType requirementType,
            @Min(0) Integer creditHours,
            @Min(0) Integer internalMarks,
            @Min(0) Integer externalMarks,
            @Size(max = 60) String electiveGroup,
            @Min(1) Integer ordinal) {
    }

    public record CurriculumCourseResponse(
            UUID id,
            UUID courseId,
            String courseCode,
            String courseName,
            Integer courseCredits,
            UUID semesterId,
            String semesterName,
            Integer semesterOrdinal,
            CurriculumCourse.RequirementType requirementType,
            Integer creditHours,
            Integer internalMarks,
            Integer externalMarks,
            Integer totalMarks,
            String electiveGroup,
            Integer ordinal) {
    }

    // ------------------------------------------------------------------ semesters

    public record CreateSemester(
            @NotNull UUID academicYearId,
            @NotBlank @Size(max = 60) String name,
            @NotNull @Min(1) Integer ordinal,
            @NotNull LocalDate startDate,
            @NotNull LocalDate endDate,
            Semester.TermType type) {
    }

    public record SemesterResponse(
            UUID id,
            UUID academicYearId,
            String academicYearName,
            String name,
            Integer ordinal,
            LocalDate startDate,
            LocalDate endDate,
            Semester.TermType type,
            Semester.Status status,
            long courseOfferingCount) {
    }

    // ------------------------------------------------------------------- sections

    public record CreateSection(
            @NotNull UUID schoolClassId,
            @NotBlank @Size(max = 60) String name,
            @NotBlank @Size(max = 20) String code,
            @Min(1) Integer capacity,
            @Size(max = 60) String room) {
    }

    public record SectionResponse(
            UUID id,
            UUID schoolClassId,
            String schoolClassName,
            String name,
            String code,
            Integer capacity,
            String room,
            boolean active) {
    }

    // ---------------------------------------------------------------------- rooms

    public record CreateRoom(
            @NotBlank @Size(max = 30) String code,
            @NotBlank @Size(max = 150) String name,
            @Size(max = 100) String building,
            @Min(1) Integer capacity,
            @Size(max = 30) String roomType,
            UUID campusId) {
    }

    public record RoomResponse(
            UUID id,
            String code,
            String name,
            String building,
            Integer capacity,
            String roomType,
            UUID campusId,
            String campusName,
            boolean active) {
    }

    // ------------------------------------------------------------ course offerings

    public record CreateCourseOffering(
            @NotNull UUID courseId,
            @NotNull UUID academicYearId,
            UUID semesterId,
            UUID programId,
            UUID curriculumId,
            UUID schoolClassId,
            UUID sectionId,
            UUID teacherId,
            @Size(max = 150) String teacherName,
            UUID roomId,
            @Min(1) Integer capacity,
            @Min(0) Integer weeklyPeriods,
            @Min(0) Integer internalMarks,
            @Min(0) Integer externalMarks,
            @Min(0) Integer passMarks) {
    }

    public record UpdateCourseOffering(
            UUID teacherId,
            @Size(max = 150) String teacherName,
            UUID roomId,
            @Min(1) Integer capacity,
            @Min(0) Integer weeklyPeriods,
            @Min(0) Integer internalMarks,
            @Min(0) Integer externalMarks,
            @Min(0) Integer passMarks) {
    }

    public record CourseOfferingResponse(
            UUID id,
            String offeringCode,
            AcademicModel academicModel,
            UUID courseId,
            String courseCode,
            String courseName,
            UUID academicYearId,
            String academicYearName,
            UUID semesterId,
            String semesterName,
            UUID programId,
            String programName,
            UUID curriculumId,
            UUID schoolClassId,
            String schoolClassName,
            UUID sectionId,
            String sectionName,
            UUID teacherId,
            String teacherName,
            UUID roomId,
            String roomName,
            Integer capacity,
            Integer enrolledCount,
            Integer availableSeats,
            Integer weeklyPeriods,
            Integer internalMarks,
            Integer externalMarks,
            Integer totalMarks,
            Integer passMarks,
            boolean active) {
    }

    // ------------------------------------------------------------------ timetable

    public record CreateTimetableEntry(
            @NotNull DayOfWeek dayOfWeek,
            @NotNull UUID timeSlotId,
            @NotNull UUID courseOfferingId,
            UUID sectionId,
            UUID schoolClassId,
            UUID roomId,
            UUID teacherId,
            LocalDate effectiveFrom,
            LocalDate effectiveTo) {
    }

    public record TimetableEntryResponse(
            UUID id,
            DayOfWeek dayOfWeek,
            UUID timeSlotId,
            String timeSlotName,
            LocalTimeRange time,
            UUID courseOfferingId,
            String courseCode,
            String courseName,
            String offeringCode,
            UUID sectionId,
            String sectionName,
            UUID schoolClassId,
            String schoolClassName,
            UUID roomId,
            String roomName,
            UUID teacherId,
            String teacherName,
            LocalDate effectiveFrom,
            LocalDate effectiveTo,
            boolean active) {
    }

    /** Slot times, flattened so a grid can be drawn without a second request. */
    public record LocalTimeRange(String start, String end) {
    }

    /** One cell of a weekly grid: the entries that fall in a day and slot. */
    public record TimetableSlot(DayOfWeek dayOfWeek, UUID timeSlotId, String timeSlotName,
                                String start, String end, List<TimetableEntryResponse> entries) {
    }

    public record TimetableGrid(String scope, UUID scopeId, List<TimetableSlot> slots) {
    }
}
