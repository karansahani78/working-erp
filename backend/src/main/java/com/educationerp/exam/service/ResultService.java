package com.educationerp.exam.service;

import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
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
import com.educationerp.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
    private final GradingService grading;
    private final StudentRepository students;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    /** Enters or replaces marks for one subject. Marks are re-graded on every save. */
    @Transactional
    public List<ExamDtos.ResultResponse> enterMarks(UUID examSubjectId, ExamDtos.MarkEntryRequest request) {
        auth.requirePermission("MARKS_ENTER");
        ExamSubject subject = requireSubject(examSubjectId);
        Examination exam = requireExam(subject.getExaminationId());
        if (exam.getStatus() == Examination.Status.PUBLISHED
                || exam.getStatus() == Examination.Status.ARCHIVED) {
            throw AppException.rule("Marks cannot be entered once results are published.");
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

        switch (target) {
            case VERIFIED -> auth.requirePermission("MARKS_VERIFY");
            case APPROVED -> auth.requirePermission("MARKS_APPROVE");
            case PUBLISHED -> auth.requirePermission("RESULT_PUBLISH");
            default -> auth.requirePermission("MARKS_ENTER");
        }

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
        return corrections.findByResultIdOrderByRequestedAtDesc(resultId).stream()
                .map(ExamDtos.ResultCorrectionResponse::from)
                .toList();
    }

    // ---------- helpers ----------

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