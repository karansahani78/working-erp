package com.educationerp.exam.service;

import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.academic.CourseOffering;
import com.educationerp.academic.CourseOfferingRepository;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.exam.ExamSubject;
import com.educationerp.exam.ExamSubjectRepository;
import com.educationerp.exam.Examination;
import com.educationerp.exam.ExaminationRepository;
import com.educationerp.exam.GradeBoundary;
import com.educationerp.exam.Result;
import com.educationerp.exam.ResultCorrection;
import com.educationerp.exam.ResultCorrectionRepository;
import com.educationerp.exam.ResultRepository;
import com.educationerp.exam.dto.ExamDtos;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.UserLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Mark entry and the result lifecycle.
 *
 * <p>Grades are derived, never typed in: the service looks up the examination's grading
 * scale and applies the configured boundaries. The result workflow runs
 * DRAFT → MARKS_ENTERED → VERIFIED → APPROVED → PUBLISHED, and once published a mark
 * cannot be overwritten — the blueprint requires a reason, authorisation, audit,
 * recalculation and republish, which {@link #requestCorrection(UUID, ExamDtos.ResultCorrectionRequest)}
 * plus {@link #decideCorrection(UUID, ExamDtos.CorrectionDecision)} implement.
 */
@Service
@RequiredArgsConstructor
public class ResultService {

    private final ResultRepository results;
    private final ResultCorrectionRepository corrections;
    private final ExamSubjectRepository subjects;
    private final ExaminationRepository examinations;
    private final CourseOfferingRepository offerings;
    private final EnrollmentRepository enrollments;
    private final UserLookup users;
    private final GradingService grading;
    private final StudentRepository students;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    /** Enters or replaces marks for one subject. Marks are re-graded on every save. */
    @Transactional
    public List<ExamDtos.ResultResponse> enterMarks(UUID examSubjectId, ExamDtos.MarkEntryRequest request) {
        auth.requirePermission("MARKS_ENTER");
        ExamSubject subject = requireSubject(examSubjectId);
        requireAccessibleOffering(subject.getCourseOfferingId(), "MARKS_ENTER");
        Examination exam = requireExam(subject.getExaminationId());
        if (exam.getStatus() == Examination.Status.PUBLISHED
                || exam.getStatus() == Examination.Status.ARCHIVED) {
            throw AppException.rule("Marks cannot be entered once results are published.");
        }

        Set<UUID> roster = subject.getCourseOfferingId() == null
                ? Set.of()
                : rosterStudentIds(requireAccessibleOffering(subject.getCourseOfferingId(), "MARKS_ENTER"));
        if (!roster.isEmpty()) {
            for (ExamDtos.MarkEntry entry : request.marks()) {
                if (students.findById(entry.studentId()).isEmpty()) {
                    throw AppException.notFound("Student");
                }
                if (!roster.contains(entry.studentId())) {
                    throw AppException.denied("A student in this marks entry is not enrolled "
                            + "in the class this subject is taught to.");
                }
            }
        }

        List<Result> entered = new java.util.ArrayList<>();
        for (ExamDtos.MarkEntry entry : request.marks()) {
            Result result = results
                    .findByStudentIdAndExamSubjectId(entry.studentId(), subject.getId())
                    .orElseGet(() -> newResult(entry.studentId(), subject, entry.enrollmentId()));
            guardEditable(result);
            if (entry.marksObtained().compareTo(subject.getMaxMarks()) > 0) {
                throw AppException.rule("Marks for " + subject.getSubjectName()
                        + " cannot exceed the maximum of " + subject.getMaxMarks().toPlainString() + ".");
            }
            if (students.findById(entry.studentId()).isEmpty()) {
                throw AppException.notFound("Student");
            }

            result.setMarksObtained(entry.marksObtained());
            result.setMaxMarks(subject.getMaxMarks());
            result.setEnrollmentId(entry.enrollmentId() != null ? entry.enrollmentId() : result.getEnrollmentId());
            applyGrade(result, exam.getGradingScaleId());
            result.setStatus(Result.ResultStatus.MARKS_ENTERED);
            results.save(result);
            entered.add(result);
        }

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("MarkEntry")
                .entityId(subject.getId().toString())
                .entityLabel(subject.getSubjectName())
                .summary("Entered marks for " + request.marks().size() + " students")
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        // Only the students in this request, in the order they were sent, so a caller
        // marking one student in a large class gets a predictable response.
        return entered.stream().map(ExamDtos.ResultResponse::from).toList();
    }

    @Transactional
    public ExamDtos.ResultResponse transition(UUID resultId, ExamDtos.StatusRequest request) {
        Result result = requireResult(resultId);
        Result.ResultStatus current = result.getStatus();
        Result.ResultStatus target;
        try {
            target = Result.ResultStatus.valueOf(request.status().trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown result status.");
        }

        String permission = switch (target) {
            case VERIFIED -> "MARKS_VERIFY";
            case APPROVED -> "MARKS_APPROVE";
            case PUBLISHED -> "RESULT_PUBLISH";
            default -> "MARKS_ENTER";
        };
        auth.requirePermission(permission);
        requireAccessibleOffering(
                requireSubject(result.getExamSubjectId()).getCourseOfferingId(),
                permission);

        if (current == Result.ResultStatus.PUBLISHED) {
            throw new AppException(ErrorCode.RESULT_ALREADY_PUBLISHED,
                    "This result is published. Request a correction instead of editing it.");
        }
        if (!isAllowed(current, target)) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A result in " + current + " cannot move to " + target + ".");
        }
        if (target == Result.ResultStatus.MARKS_ENTERED && result.getMarksObtained() == null) {
            throw AppException.rule("Enter the marks before marking the result as entered.");
        }

        result.setStatus(target);
        if (target == Result.ResultStatus.PUBLISHED) {
            result.setPublishedAt(Instant.now());
        }
        results.save(result);

        audit.record(AuditEvent.builder()
                .action(target == Result.ResultStatus.PUBLISHED
                        ? com.educationerp.audit.AuditAction.PUBLISH
                        : com.educationerp.audit.AuditAction.UPDATE)
                .entityType("Result")
                .entityId(result.getId().toString())
                .entityLabel(result.getStudentId().toString())
                .summary("Result status " + current + " -> " + target)
                .before(Map.of("status", current.name()))
                .after(Map.of("status", target.name()))
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return ExamDtos.ResultResponse.from(result);
    }

    /** Bulk transition for a whole subject or examination. */
    @Transactional
    public List<ExamDtos.ResultResponse> transitionExam(UUID examinationId, ExamDtos.StatusRequest request) {
        Examination exam = requireExam(examinationId);
        List<Result> all = results.findByExaminationIdOrderByStudentIdAsc(examinationId);
        if (all.isEmpty()) {
            throw AppException.rule("There are no results to update for this examination.");
        }
        if (exam.getStatus() == Examination.Status.PUBLISHED) {
            throw new AppException(ErrorCode.RESULT_ALREADY_PUBLISHED,
                    "These results have already been published.");
        }
        List<ExamDtos.ResultResponse> updated = all.stream()
                .map(result -> transition(result.getId(), request))
                .toList();
        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("Result")
                .entityId(examinationId.toString())
                .entityLabel(exam.getCode())
                .summary("Bulk result transition to " + request.status().toUpperCase()
                        + " for " + updated.size() + " results")
                .module("EXAMINATION")
                .succeeded(true)
                .build());
        return updated;
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.ResultResponse> forExamination(UUID examinationId) {
        auth.requirePermission("RESULT_READ");
        return results.findByExaminationIdOrderByStudentIdAsc(examinationId).stream()
                .map(ExamDtos.ResultResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.ResultResponse> forStudent(UUID studentId, Result.ResultStatus status) {
        auth.requireStudentAccess(studentId);
        List<Result> found = status == null
                ? results.findByStudentId(studentId)
                : results.findByStudentIdAndStatusOrderByCreatedAtAsc(studentId, status);
        return found.stream().map(ExamDtos.ResultResponse::from).toList();
    }

    // ---------- Corrections ----------

    /**
     * Requests a change to a published result. The result is left untouched until the
     * request is approved, and the old and new values are both recorded.
     */
    @Transactional
    public ExamDtos.ResultCorrectionResponse requestCorrection(UUID resultId,
                                                                ExamDtos.ResultCorrectionRequest request) {
        auth.requirePermission("RESULT_CORRECT");
        Result result = requireResult(resultId);
        requireAccessibleOffering(
                requireSubject(result.getExamSubjectId()).getCourseOfferingId(),
                "RESULT_CORRECT");
        if (result.getStatus() != Result.ResultStatus.PUBLISHED) {
            throw AppException.rule("A correction is only needed for a published result. "
                    + "This result is " + result.getStatus() + ".");
        }
        if (request.newMarks().compareTo(result.getMaxMarks()) > 0) {
            throw AppException.rule("Corrected marks cannot exceed the maximum of "
                    + result.getMaxMarks().toPlainString() + ".");
        }

        ResultCorrection correction = new ResultCorrection();
        correction.setResultId(result.getId());
        correction.setOldMarks(result.getMarksObtained());
        correction.setNewMarks(request.newMarks());
        correction.setOldGrade(result.getLetterGrade());
        correction.setReason(request.reason().trim());
        correction.setStatus(ResultCorrection.Status.PENDING);
        correction.setRequestedBy(currentUserId());
        correction.setRequestedAt(Instant.now());
        corrections.save(correction);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.CORRECTION)
                .entityType("ResultCorrection")
                .entityId(correction.getId().toString())
                .entityLabel(result.getStudentId().toString())
                .summary("Requested correction of a published result")
                .before(Map.of("marks", String.valueOf(result.getMarksObtained())))
                .after(Map.of("marks", request.newMarks().toPlainString()))
                .metadata(Map.of("reason", correction.getReason(), "resultId", result.getId().toString()))
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return ExamDtos.ResultCorrectionResponse.from(correction);
    }

    /** Approving a correction recalculates the grade, applies the change and republishes. */
    @Transactional
    public ExamDtos.ResultCorrectionResponse decideCorrection(UUID correctionId,
                                                              ExamDtos.CorrectionDecision decision) {
        auth.requirePermission("MARKS_APPROVE");
        ResultCorrection correction = corrections.findById(correctionId)
                .orElseThrow(() -> AppException.notFound("Result correction"));
        if (correction.getStatus() != ResultCorrection.Status.PENDING) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "This correction has already been " + correction.getStatus() + ".");
        }
        Result result = requireResult(correction.getResultId());
        requireAccessibleOffering(
                requireSubject(result.getExamSubjectId()).getCourseOfferingId(),
                "MARKS_APPROVE");

        if (!decision.approved()) {
            correction.setStatus(ResultCorrection.Status.REJECTED);
            correction.setApprovedBy(currentUserId());
            correction.setApprovedAt(Instant.now());
            correction.setApprovalNotes(decision.notes());
            corrections.save(correction);
            return ExamDtos.ResultCorrectionResponse.from(correction);
        }

        Examination exam = requireExam(result.getExaminationId());
        result.setMarksObtained(correction.getNewMarks());
        applyGrade(result, exam.getGradingScaleId());
        result.setStatus(Result.ResultStatus.PUBLISHED);
        result.setPublishedAt(Instant.now());
        results.save(result);

        correction.setNewGrade(result.getLetterGrade());
        correction.setStatus(ResultCorrection.Status.APPLIED);
        correction.setApprovedBy(currentUserId());
        correction.setApprovedAt(Instant.now());
        correction.setAppliedAt(Instant.now());
        correction.setApprovalNotes(decision.notes());
        corrections.save(correction);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.RESULT_CHANGE)
                .entityType("ResultCorrection")
                .entityId(correction.getId().toString())
                .entityLabel(result.getStudentId().toString())
                .summary("Applied correction to a published result and republished it")
                .before(Map.of("marks", String.valueOf(correction.getOldMarks()),
                        "grade", String.valueOf(correction.getOldGrade())))
                .after(Map.of("marks", correction.getNewMarks().toPlainString(),
                        "grade", String.valueOf(result.getLetterGrade())))
                .metadata(Map.of("reason", correction.getReason()))
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return ExamDtos.ResultCorrectionResponse.from(correction);
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.ResultCorrectionResponse> correctionsFor(UUID resultId) {
        auth.requirePermission("RESULT_READ");
        requireAccessibleOffering(
                requireSubject(requireResult(resultId).getExamSubjectId()).getCourseOfferingId(),
                "RESULT_READ");
        return corrections.findByResultIdOrderByRequestedAtDesc(resultId).stream()
                .map(ExamDtos.ResultCorrectionResponse::from)
                .toList();
    }

    // ---------- helpers ----------

    /**
     * A verifier or controller of results (leadership, exam office, administrators) works
     * across every subject; an examiner is limited to the class linked to the subject.
     */
    private boolean resultSupervisor() {
        return auth.hasPermission("MARKS_APPROVE");
    }

    /**
     * Loads the offering behind a subject only when the caller may act on it. A subject
     * with no class link can only be handled by exam leadership, because a teacher cannot
     * be held to their own class for something that has none. Returns the offering, or
     * {@code null} for a supervisor handling an unlinked subject.
     */
    private CourseOffering requireAccessibleOffering(UUID courseOfferingId, String permission) {
        auth.requirePermission(permission);
        if (courseOfferingId == null) {
            if (resultSupervisor()) {
                return null;
            }
            throw AppException.denied("Separate a subject that is not tied to a class "
                    + "before entering or changing its results.");
        }
        CourseOffering offering = offerings.findById(courseOfferingId)
                .orElseThrow(() -> AppException.notFound("Course offering"));
        if (resultSupervisor()) {
            return offering;
        }
        UUID employeeId = users.employeeIdOf(auth.requireUser().userId())
                .orElseThrow(() -> AppException.denied("You do not have permission to perform this action."));
        if (offering.getTeacherId() == null || !offering.getTeacherId().equals(employeeId)) {
            throw AppException.denied("You are not assigned to the class that takes this subject.");
        }
        return offering;
    }

    /** The roster of the class the subject is taught to, for the marks entry check. */
    private Set<UUID> rosterStudentIds(CourseOffering offering) {
        Set<UUID> ids = new HashSet<>();
        if (offering == null || offering.getAcademicYear() == null || offering.getSchoolClass() == null) {
            return ids;
        }
        for (Enrollment enrolment : enrollments
                .findByAcademicYearIdAndSchoolClassIdAndStatusOrderByRollNumberAsc(
                        offering.getAcademicYear().getId(), offering.getSchoolClass().getId(),
                        Enrollment.Status.ACTIVE)) {
            if (offering.getSection() == null
                    || offering.getSection().getId().equals(enrolment.getSectionId())) {
                ids.add(enrolment.getStudentId());
            }
        }
        return ids;
    }

    /**
     * Recalculates percentage, letter grade, grade point and pass flag from the mark and
     * the examination's configured scale. This is the "recalculation" step of a correction.
     */
    private void applyGrade(Result result, UUID gradingScaleId) {
        if (result.getMarksObtained() == null || result.getMaxMarks() == null
                || result.getMaxMarks().compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        BigDecimal percentage = result.getMarksObtained()
                .multiply(BigDecimal.valueOf(100))
                .divide(result.getMaxMarks(), 2, RoundingMode.HALF_UP);
        result.setPercentage(percentage);

        GradeBoundary boundary = grading.resolve(gradingScaleId, percentage);
        if (boundary != null) {
            result.setLetterGrade(boundary.getLetterGrade());
            result.setGradePoint(boundary.getGradePoint());
            result.setPass(boundary.isPass());
        } else {
            result.setLetterGrade(null);
            result.setGradePoint(null);
            result.setPass(null);
        }
    }

    private boolean isAllowed(Result.ResultStatus current, Result.ResultStatus target) {
        return switch (current) {
            case DRAFT -> target == Result.ResultStatus.MARKS_ENTERED;
            case MARKS_ENTERED -> target == Result.ResultStatus.VERIFIED
                    || target == Result.ResultStatus.MARKS_ENTERED;
            case VERIFIED -> target == Result.ResultStatus.APPROVED
                    || target == Result.ResultStatus.MARKS_ENTERED;
            case APPROVED -> target == Result.ResultStatus.PUBLISHED
                    || target == Result.ResultStatus.VERIFIED;
            case PUBLISHED -> false;
        };
    }

    private void guardEditable(Result result) {
        if (result.getStatus() == Result.ResultStatus.APPROVED
                || result.getStatus() == Result.ResultStatus.PUBLISHED) {
            throw new AppException(ErrorCode.RESULT_LOCKED,
                    "This result is " + result.getStatus() + " and can no longer be edited.");
        }
    }

    private Result newResult(UUID studentId, ExamSubject subject, UUID enrollmentId) {
        Result result = new Result();
        result.setStudentId(studentId);
        result.setExaminationId(subject.getExaminationId());
        result.setExamSubjectId(subject.getId());
        result.setEnrollmentId(enrollmentId);
        result.setMaxMarks(subject.getMaxMarks());
        result.setStatus(Result.ResultStatus.DRAFT);
        return result;
    }

    private ExamSubject requireSubject(UUID id) {
        return subjects.findById(id).orElseThrow(() -> AppException.notFound("Exam subject"));
    }

    private Examination requireExam(UUID id) {
        return examinations.findById(id).orElseThrow(() -> AppException.notFound("Examination"));
    }

    private Result requireResult(UUID id) {
        return results.findById(id).orElseThrow(() -> AppException.notFound("Result"));
    }

    private UUID currentUserId() {
        var user = auth.currentUser();
        return user == null ? null : user.userId();
    }
}