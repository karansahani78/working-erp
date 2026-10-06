package com.educationerp.exam.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import com.educationerp.exam.ExamSubject;
import com.educationerp.exam.Examination;
import com.educationerp.exam.GradeBoundary;
import com.educationerp.exam.GradingScale;
import com.educationerp.exam.ReportCard;
import com.educationerp.exam.ReportCardItem;
import com.educationerp.exam.Result;
import com.educationerp.exam.ResultCorrection;

/** DTOs for the examination module. No entity is ever returned directly. */
public final class ExamDtos {

    private ExamDtos() {
    }

    // ---------- Grading ----------

    public record GradeBoundaryRequest(
            @NotBlank @Size(max = 10) String letterGrade,
            @NotNull @DecimalMin("0.00") BigDecimal gradePoint,
            @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal minPercentage,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal maxPercentage,
            Boolean pass
    ) {
    }

    public record GradingScaleRequest(
            @NotBlank @Size(max = 100) String name,
            @NotBlank @Size(max = 30) String code,
            @Size(max = 300) String description,
            Boolean active,
            @NotEmpty @Valid List<GradeBoundaryRequest> boundaries
    ) {
    }

    public record GradeBoundaryResponse(
            UUID id,
            String letterGrade,
            BigDecimal gradePoint,
            BigDecimal minPercentage,
            BigDecimal maxPercentage,
            boolean pass
    ) {
        public static GradeBoundaryResponse from(GradeBoundary boundary) {
            return new GradeBoundaryResponse(boundary.getId(), boundary.getLetterGrade(),
                    boundary.getGradePoint(), boundary.getMinPercentage(),
                    boundary.getMaxPercentage(), boundary.isPass());
        }
    }

    public record GradingScaleResponse(
            UUID id,
            String name,
            String code,
            String description,
            boolean active,
            List<GradeBoundaryResponse> boundaries
    ) {
        public static GradingScaleResponse from(GradingScale scale, List<GradeBoundary> boundaries) {
            return new GradingScaleResponse(scale.getId(), scale.getName(), scale.getCode(),
                    scale.getDescription(), scale.isActive(),
                    boundaries.stream().map(GradeBoundaryResponse::from).toList());
        }
    }

    // ---------- Examination ----------

    public record ExaminationRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 40) String code,
            UUID academicYearId,
            UUID semesterId,
            String examType,
            LocalDate startDate,
            LocalDate endDate,
            UUID gradingScaleId,
            @PositiveOrZero BigDecimal maxTotalMarks,
            @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal passPercentage,
            String status
    ) {
    }

    public record ExaminationResponse(
            UUID id,
            String name,
            String code,
            UUID academicYearId,
            UUID semesterId,
            String examType,
            LocalDate startDate,
            LocalDate endDate,
            UUID gradingScaleId,
            BigDecimal maxTotalMarks,
            BigDecimal passPercentage,
            String status
    ) {
        public static ExaminationResponse from(Examination exam) {
            return new ExaminationResponse(exam.getId(), exam.getName(), exam.getCode(),
                    exam.getAcademicYearId(), exam.getSemesterId(), exam.getExamType().name(),
                    exam.getStartDate(), exam.getEndDate(), exam.getGradingScaleId(),
                    exam.getMaxTotalMarks(), exam.getPassPercentage(), exam.getStatus().name());
        }
    }

    public record StatusRequest(@NotBlank String status) {
    }

    // ---------- Exam schedule ----------

    public record ExamSubjectRequest(
            @NotBlank @Size(max = 150) String subjectName,
            @Size(max = 40) String subjectCode,
            UUID courseOfferingId,
            LocalDate examDate,
            LocalTime startTime,
            LocalTime endTime,
            @NotNull @PositiveOrZero BigDecimal maxMarks,
            @PositiveOrZero BigDecimal passMarks,
            UUID roomId
    ) {
    }

    public record ExamScheduleRequest(
            @NotEmpty @Valid List<ExamSubjectRequest> subjects
    ) {
    }

    public record ExamSubjectResponse(
            UUID id,
            UUID examinationId,
            UUID courseOfferingId,
            String subjectName,
            String subjectCode,
            LocalDate examDate,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal maxMarks,
            BigDecimal passMarks,
            UUID roomId
    ) {
        public static ExamSubjectResponse from(ExamSubject subject) {
            return new ExamSubjectResponse(subject.getId(), subject.getExaminationId(),
                    subject.getCourseOfferingId(), subject.getSubjectName(), subject.getSubjectCode(),
                    subject.getExamDate(), subject.getStartTime(), subject.getEndTime(),
                    subject.getMaxMarks(), subject.getPassMarks(), subject.getRoomId());
        }
    }

    // ---------- Marks and results ----------

    /** One student's mark for one subject. */
    public record MarkEntry(
            @NotNull UUID studentId,
            @NotNull @DecimalMin("0.00") BigDecimal marksObtained,
            UUID enrollmentId
    ) {
    }

    /** Marks for one subject across a class of students. The subject comes from the path. */
    public record MarkEntryRequest(
            @NotEmpty @Valid List<MarkEntry> marks
    ) {
    }

    public record ResultResponse(
            UUID id,
            UUID studentId,
            UUID examinationId,
            UUID examSubjectId,
            UUID enrollmentId,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage,
            String letterGrade,
            BigDecimal gradePoint,
            Boolean pass,
            String status,
            Instant publishedAt
    ) {
        public static ResultResponse from(Result result) {
            return new ResultResponse(result.getId(), result.getStudentId(), result.getExaminationId(),
                    result.getExamSubjectId(), result.getEnrollmentId(), result.getMarksObtained(),
                    result.getMaxMarks(), result.getPercentage(), result.getLetterGrade(),
                    result.getGradePoint(), result.getPass(), result.getStatus().name(),
                    result.getPublishedAt());
        }
    }

    public record ResultCorrectionRequest(
            @NotNull @DecimalMin("0.00") BigDecimal newMarks,
            @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record CorrectionDecision(
            boolean approved,
            @Size(max = 500) String notes
    ) {
    }

    public record ResultCorrectionResponse(
            UUID id,
            UUID resultId,
            BigDecimal oldMarks,
            BigDecimal newMarks,
            String oldGrade,
            String newGrade,
            String reason,
            String status,
            UUID requestedBy,
            Instant requestedAt,
            UUID approvedBy,
            Instant approvedAt,
            Instant appliedAt,
            String approvalNotes
    ) {
        public static ResultCorrectionResponse from(ResultCorrection correction) {
            return new ResultCorrectionResponse(correction.getId(), correction.getResultId(),
                    correction.getOldMarks(), correction.getNewMarks(), correction.getOldGrade(),
                    correction.getNewGrade(), correction.getReason(), correction.getStatus().name(),
                    correction.getRequestedBy(), correction.getRequestedAt(), correction.getApprovedBy(),
                    correction.getApprovedAt(), correction.getAppliedAt(), correction.getApprovalNotes());
        }
    }

    // ---------- Report cards and transcripts ----------

    public record ReportCardItemResponse(
            String subjectName,
            String subjectCode,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal credits,
            String letterGrade,
            BigDecimal gradePoint,
            Boolean pass
    ) {
        public static ReportCardItemResponse from(ReportCardItem item) {
            return new ReportCardItemResponse(item.getSubjectName(), item.getSubjectCode(),
                    item.getMarksObtained(), item.getMaxMarks(), item.getCredits(),
                    item.getLetterGrade(), item.getGradePoint(), item.getPass());
        }
    }

    public record ReportCardResponse(
            UUID id,
            UUID studentId,
            UUID academicYearId,
            UUID semesterId,
            String referenceCode,
            BigDecimal totalMarks,
            BigDecimal totalCredits,
            BigDecimal gpa,
            BigDecimal cgpa,
            BigDecimal attendancePercentage,
            String overallResult,
            String status,
            Instant publishedAt,
            List<ReportCardItemResponse> items
    ) {
    }

    public record TranscriptResponse(
            UUID id,
            UUID studentId,
            String referenceCode,
            Instant generatedAt,
            BigDecimal totalCredits,
            BigDecimal cumulativeGpa,
            BigDecimal cumulativeCgpa,
            String status,
            Instant finalisedAt,
            List<ReportCardResponse> reportCards
    ) {
    }
}