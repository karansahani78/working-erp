package com.educationerp.student.dto;

import com.educationerp.finance.dto.FinanceDtos;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** DTOs for the admissions and students module. No entity is ever returned directly. */
public final class StudentDtos {

    private StudentDtos() {
    }

    // ---------- Campaign ----------

    public record CampaignRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Size(max = 40) String code,
            UUID academicYearId,
            LocalDate openDate,
            LocalDate closeDate,
            @PositiveOrZero BigDecimal applicationFee,
            Integer capacity,
            String status,
            List<String> documentRequirements
    ) {
    }

    public record CampaignResponse(
            UUID id,
            String name,
            String code,
            UUID academicYearId,
            LocalDate openDate,
            LocalDate closeDate,
            BigDecimal applicationFee,
            Integer capacity,
            String status,
            List<String> documentRequirements
    ) {
    }

    // ---------- Application ----------

    public record ApplicationRequest(
            @NotNull UUID campaignId,
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @Size(max = 100) String lastName,
            LocalDate dateOfBirth,
            String gender,
            @Size(max = 80) String nationality,
            @Size(max = 60) String phone,
            @Size(max = 180) String email,
            @Size(max = 400) String address,
            @Size(max = 400) String photoUrl,
            UUID applyingProgramId,
            UUID applyingClassId,
            @Size(max = 200) String previousSchool,
            @Size(max = 120) String previousQualification,
            BigDecimal previousPercentage,
            BigDecimal entranceScore,
            BigDecimal meritScore
    ) {
    }

    public record ApplicationResponse(
            UUID id,
            UUID campaignId,
            String referenceCode,
            String firstName,
            String middleName,
            String lastName,
            LocalDate dateOfBirth,
            String gender,
            String nationality,
            String phone,
            String email,
            String address,
            String photoUrl,
            UUID applyingProgramId,
            UUID applyingClassId,
            String previousSchool,
            String previousQualification,
            BigDecimal previousPercentage,
            BigDecimal entranceScore,
            BigDecimal meritScore,
            String status,
            LocalDate submittedAt,
            LocalDate decidedAt,
            String decisionNotes,
            UUID studentId,
            Instant createdAt
    ) {
    }

    /** Minimal update payload; absent fields keep their current value. */
    public record ApplicationUpdate(
            @Size(max = 60) String phone,
            @Size(max = 180) String email,
            @Size(max = 400) String address,
            BigDecimal entranceScore,
            BigDecimal meritScore
    ) {
    }

    // ---------- Document ----------

    public record DocumentRequest(
            @NotBlank @Size(max = 60) String documentType,
            @NotBlank @Size(max = 255) String fileName,
            @NotBlank @Size(max = 400) String storageKey,
            @Size(max = 120) String contentType,
            Long sizeBytes
    ) {
    }

    public record DocumentResponse(
            UUID id,
            UUID applicationId,
            String documentType,
            String fileName,
            String storageKey,
            String contentType,
            Long sizeBytes,
            String status,
            String reviewNotes,
            Instant reviewedAt
    ) {
    }

    /** Outcome of verifying one document during the verification stage. */
    public record DocumentVerification(
            @NotBlank String documentType,
            @NotBlank String status,
            @Size(max = 500) String notes
    ) {
    }

    // ---------- Decision ----------

    public record DecisionRequest(
            @NotBlank String decision,
            @Size(max = 1000) String notes
    ) {
    }

    public record DecisionResponse(
            UUID id,
            UUID applicationId,
            String decision,
            UUID decidedBy,
            Instant decidedAt,
            String notes
    ) {
    }

    // ---------- Student ----------

    public record StudentRequest(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @Size(max = 100) String lastName,
            LocalDate dateOfBirth,
            String gender,
            @Size(max = 80) String nationality,
            @Size(max = 60) String phone,
            @Size(max = 180) String email,
            @Size(max = 400) String address,
            @Size(max = 400) String photoUrl,
            String status
    ) {
    }

    public record StudentResponse(
            UUID id,
            String studentNumber,
            UUID userId,
            UUID admissionId,
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
            LocalDate enrollmentDate,
            String status,
            Instant createdAt
    ) {
    }

    // ---------- Guardian ----------

    public record GuardianRequest(
            @NotBlank @Size(max = 100) String firstName,
            @Size(max = 100) String middleName,
            @Size(max = 100) String lastName,
            @Size(max = 60) String phone,
            @Size(max = 180) String email,
            @Size(max = 150) String occupation,
            @Size(max = 400) String address
    ) {
    }

    public record GuardianResponse(
            UUID id,
            String firstName,
            String middleName,
            String lastName,
            String fullName,
            String phone,
            String email,
            String occupation,
            String address,
            UUID userId,
            boolean accountLinked
    ) {
    }

    /**
     * Links a guardian to the sign-in account they will use. The account is named by email
     * rather than by id so a registrar does not have to go looking for a uuid, and so the
     * same account cannot be attached to two guardians by a mistyped id.
     */
    public record GuardianAccountLinkRequest(
            @jakarta.validation.constraints.Email
            @jakarta.validation.constraints.NotBlank String email
    ) {
    }

    /** Linking a guardian to a student; one guardian may hold several such links. */
    public record GuardianLinkRequest(
            @NotNull UUID guardianId,
            @NotBlank String relationship,
            boolean primaryContact,
            boolean canPickup
    ) {
    }

    public record GuardianLinkResponse(
            UUID id,
            UUID guardianId,
            String guardianName,
            String phone,
            String email,
            String relationship,
            boolean primaryContact,
            boolean canPickup
    ) {
    }

    // ---------- Enrolment ----------

    public record EnrollmentRequest(
            @NotNull UUID studentId,
            UUID academicYearId,
            UUID semesterId,
            UUID schoolClassId,
            UUID sectionId,
            @Size(max = 20) String rollNumber
    ) {
    }

    public record EnrollmentResponse(
            UUID id,
            UUID studentId,
            UUID academicYearId,
            UUID semesterId,
            UUID schoolClassId,
            UUID sectionId,
            String rollNumber,
            String status,
            LocalDate enrolledAt
    ) {
    }

    // ---------- Student 360 ----------

    /**
     * Aggregate read model for the Student 360 screen. Each tab is populated only from
     * modules that exist so far, so the endpoint stays useful as later phases land.
     */
    /**
     * One aggregated view. Each tab is owned by the module that supplies it, so a tab is
     * absent rather than empty when its module has no data for this student yet.
     */
    public record Student360(
            StudentResponse profile,
            List<EnrollmentResponse> enrollments,
            List<GuardianLinkResponse> guardians,
            List<ApplicationResponse> admissions,
            String fullName,
            List<AttendanceSummary> attendance,
            List<ResultSummary> results,
            List<ReportCardSummary> reportCards,
            FinanceDtos.StudentFeeSummary finance
    ) {
    }

    /** Attendance tab: the whole-year percentage plus the period-by-period breakdown. */
    public record AttendanceSummary(
            String attendancePercentage,
            long totalPeriods,
            long presentPeriods,
            long absentPeriods,
            long latePeriods,
            long excusedPeriods
    ) {
    }

    /** Result tab: one line per exam subject the student sat. */
    public record ResultSummary(
            UUID examSubjectId,
            String examinationName,
            String subjectName,
            String subjectCode,
            BigDecimal marksObtained,
            BigDecimal maxMarks,
            BigDecimal percentage,
            String letterGrade,
            BigDecimal gradePoint,
            Boolean pass,
            String status
    ) {
    }

    public record ReportCardSummary(
            UUID id,
            String referenceCode,
            BigDecimal totalMarks,
            BigDecimal gpa,
            String overallResult,
            String status
    ) {
    }
}