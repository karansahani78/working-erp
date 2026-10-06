package com.educationerp.exam.service;

import com.educationerp.academic.AcademicYearRepository;
import com.educationerp.academic.CourseOfferingRepository;
import com.educationerp.academic.RoomRepository;
import com.educationerp.academic.SemesterRepository;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.exam.ExamSubject;
import com.educationerp.exam.ExamSubjectRepository;
import com.educationerp.exam.Examination;
import com.educationerp.exam.ExaminationRepository;
import com.educationerp.exam.GradingScaleRepository;
import com.educationerp.exam.Result;
import com.educationerp.exam.ResultRepository;
import com.educationerp.exam.dto.ExamDtos;
import com.educationerp.communication.CommunicationEventCode;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.MessageEvent;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Examinations and their subject schedule.
 *
 * <p>Two invariants matter here. Subject times must not overlap inside one exam (a
 * student cannot sit two papers at once), and a paper cannot be scheduled outside the
 * exam's own date window. Marks for an exam can only be entered once it has started.
 */
@Service
@RequiredArgsConstructor
public class ExaminationService {

    private final ExaminationRepository examinations;
    private final ExamSubjectRepository subjects;
    private final GradingScaleRepository scales;
    private final ResultRepository results;
    private final AcademicYearRepository years;
    private final SemesterRepository semesters;
    private final CourseOfferingRepository offerings;
    private final RoomRepository rooms;
    private final AuthorizationChecker auth;
    private final AuditService audit;
    private final CommunicationService communication;
    private final StudentRepository students;
    private final ApplicationEventPublisher events;

    private static final Map<Examination.Status, Set<Examination.Status>> TRANSITIONS = Map.of(
            Examination.Status.PLANNED, Set.of(Examination.Status.SCHEDULED, Examination.Status.ARCHIVED),
            Examination.Status.SCHEDULED, Set.of(Examination.Status.IN_PROGRESS, Examination.Status.ARCHIVED),
            Examination.Status.IN_PROGRESS, Set.of(Examination.Status.MARKS_ENTERED),
            Examination.Status.MARKS_ENTERED, Set.of(Examination.Status.VERIFIED),
            Examination.Status.VERIFIED, Set.of(Examination.Status.APPROVED, Examination.Status.MARKS_ENTERED),
            Examination.Status.APPROVED, Set.of(Examination.Status.PUBLISHED),
            Examination.Status.PUBLISHED, Set.of(Examination.Status.ARCHIVED),
            Examination.Status.ARCHIVED, Set.of()
    );

    @Transactional(readOnly = true)
    public List<ExamDtos.ExaminationResponse> list() {
        auth.requirePermission("EXAM_READ");
        return examinations.findAll().stream().map(ExamDtos.ExaminationResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ExamDtos.ExaminationResponse get(UUID id) {
        auth.requirePermission("EXAM_READ");
        return ExamDtos.ExaminationResponse.from(requireExam(id));
    }

    @Transactional
    public ExamDtos.ExaminationResponse create(ExamDtos.ExaminationRequest request) {
        auth.requirePermission("EXAM_CREATE");
        if (examinations.existsByCodeIgnoreCase(request.code().trim())) {
            throw AppException.duplicate("An examination with this code already exists.");
        }
        validate(request);

        Examination exam = new Examination();
        apply(exam, request);
        examinations.save(exam);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.CREATE)
                .entityType("Examination")
                .entityId(exam.getId().toString())
                .entityLabel(exam.getName())
                .summary("Created examination " + exam.getCode())
                .after(Map.of("code", exam.getCode(), "status", exam.getStatus().name()))
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return ExamDtos.ExaminationResponse.from(exam);
    }

    @Transactional
    public ExamDtos.ExaminationResponse update(UUID id, ExamDtos.ExaminationRequest request) {
        auth.requirePermission("EXAM_CREATE");
        Examination exam = requireExam(id);
        if (exam.getStatus() == Examination.Status.PUBLISHED
                || exam.getStatus() == Examination.Status.ARCHIVED) {
            throw AppException.rule("A " + exam.getStatus() + " examination cannot be edited.");
        }
        examinations.findByCodeIgnoreCase(request.code().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw AppException.duplicate("An examination with this code already exists.");
                });
        validate(request);
        apply(exam, request);
        examinations.save(exam);
        return ExamDtos.ExaminationResponse.from(exam);
    }

    @Transactional
    public ExamDtos.ExaminationResponse transition(UUID id, ExamDtos.StatusRequest request) {
        auth.requirePermission("EXAM_CREATE");
        Examination exam = requireExam(id);
        Examination.Status target;
        try {
            target = Examination.Status.valueOf(request.status().trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown examination status.");
        }
        Examination.Status current = exam.getStatus();
        if (!TRANSITIONS.getOrDefault(current, Set.of()).contains(target)) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "An examination in " + current + " cannot move to " + target + ".");
        }
        if (target == Examination.Status.VERIFIED && !hasAllMarksEntered(exam.getId())) {
            throw AppException.rule("Every subject must have marks entered before verification.");
        }
        if (target == Examination.Status.PUBLISHED && !examsAreApproved(exam.getId())) {
            throw AppException.rule("Every result must be approved before this examination can be published.");
        }

        exam.setStatus(target);
        examinations.save(exam);
        if (target == Examination.Status.PUBLISHED) {
            notifyResultsPublished(exam);
        }

        audit.record(AuditEvent.builder()
                .action(target == Examination.Status.PUBLISHED
                        ? com.educationerp.audit.AuditAction.PUBLISH
                        : com.educationerp.audit.AuditAction.UPDATE)
                .entityType("Examination")
                .entityId(exam.getId().toString())
                .entityLabel(exam.getName())
                .summary("Examination status " + current + " -> " + target)
                .before(Map.of("status", current.name()))
                .after(Map.of("status", target.name()))
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return ExamDtos.ExaminationResponse.from(exam);
    }

    // ---------- Exam schedule ----------

    @Transactional(readOnly = true)
    public List<ExamDtos.ExamSubjectResponse> schedule(UUID examinationId) {
        auth.requirePermission("EXAM_READ");
        return subjects.findByExaminationIdOrderBySubjectNameAsc(examinationId).stream()
                .map(ExamDtos.ExamSubjectResponse::from)
                .toList();
    }

    /** Replaces the whole schedule. Existing results for changed subjects are not kept. */
    @Transactional
    public List<ExamDtos.ExamSubjectResponse> replaceSchedule(UUID examinationId,
                                                              ExamDtos.ExamScheduleRequest request) {
        auth.requirePermission("EXAM_SCHEDULE_MANAGE");
        Examination exam = requireExam(examinationId);
        if (exam.getStatus() == Examination.Status.PUBLISHED
                || exam.getStatus() == Examination.Status.ARCHIVED) {
            throw AppException.rule("The schedule cannot be changed once results are published.");
        }
        for (ExamDtos.ExamSubjectRequest subject : request.subjects()) {
            validateSubject(exam, subject);
        }
        rejectOverlaps(request.subjects());

        subjects.deleteAll(subjects.findByExaminationIdOrderBySubjectNameAsc(examinationId));

        List<ExamSubject> created = request.subjects().stream().map(subject -> {
            ExamSubject entity = new ExamSubject();
            entity.setExaminationId(examinationId);
            entity.setSubjectName(subject.subjectName().trim());
            entity.setSubjectCode(subject.subjectCode());
            entity.setCourseOfferingId(subject.courseOfferingId());
            entity.setExamDate(subject.examDate());
            entity.setStartTime(subject.startTime());
            entity.setEndTime(subject.endTime());
            entity.setMaxMarks(subject.maxMarks());
            entity.setPassMarks(subject.passMarks());
            entity.setRoomId(subject.roomId());
            return entity;
        }).toList();
        subjects.saveAll(created);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("ExamSchedule")
                .entityId(examinationId.toString())
                .entityLabel(exam.getCode())
                .summary("Replaced exam schedule with " + created.size() + " subjects")
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return created.stream().map(ExamDtos.ExamSubjectResponse::from).toList();
    }

    // ---------- helpers ----------

    private void validate(ExamDtos.ExaminationRequest request) {
        if (request.startDate() != null && request.endDate() != null
                && request.endDate().isBefore(request.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The examination end date cannot be before its start date.");
        }
        if (request.academicYearId() != null && years.findById(request.academicYearId()).isEmpty()) {
            throw AppException.notFound("Academic year");
        }
        if (request.semesterId() != null) {
            var semester = semesters.findById(request.semesterId())
                    .orElseThrow(() -> AppException.notFound("Semester"));
            if (request.academicYearId() != null
                    && !semester.getAcademicYear().getId().equals(request.academicYearId())) {
                throw AppException.rule("The selected semester belongs to a different academic year.");
            }
        }
        if (request.gradingScaleId() != null && scales.findById(request.gradingScaleId()).isEmpty()) {
            throw AppException.notFound("Grading scale");
        }
    }

    private void validateSubject(Examination exam, ExamDtos.ExamSubjectRequest subject) {
        if (subject.passMarks() != null && subject.passMarks().compareTo(subject.maxMarks()) > 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Pass marks cannot exceed maximum marks for " + subject.subjectName() + ".");
        }
        if (subject.startTime() != null && subject.endTime() != null
                && !subject.endTime().isAfter(subject.startTime())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The end time must be after the start time for " + subject.subjectName() + ".");
        }
        if (subject.examDate() != null && exam.getStartDate() != null
                && subject.examDate().isBefore(exam.getStartDate())) {
            throw AppException.rule(subject.subjectName() + " is scheduled before the examination starts.");
        }
        if (subject.examDate() != null && exam.getEndDate() != null
                && subject.examDate().isAfter(exam.getEndDate())) {
            throw AppException.rule(subject.subjectName() + " is scheduled after the examination ends.");
        }
        if (subject.courseOfferingId() != null
                && offerings.findById(subject.courseOfferingId()).isEmpty()) {
            throw AppException.notFound("Course offering");
        }
        if (subject.roomId() != null && rooms.findById(subject.roomId()).isEmpty()) {
            throw AppException.notFound("Room");
        }
    }

    /** Rejects two subjects sharing the same date with overlapping times. */
    private void rejectOverlaps(List<ExamDtos.ExamSubjectRequest> requested) {
        for (int i = 0; i < requested.size(); i++) {
            for (int j = i + 1; j < requested.size(); j++) {
                ExamDtos.ExamSubjectRequest a = requested.get(i);
                ExamDtos.ExamSubjectRequest b = requested.get(j);
                if (a.examDate() == null || !a.examDate().equals(b.examDate())
                        || a.startTime() == null || a.endTime() == null
                        || b.startTime() == null || b.endTime() == null) {
                    continue;
                }
                boolean overlaps = a.startTime().isBefore(b.endTime()) && b.startTime().isBefore(a.endTime());
                if (overlaps) {
                    throw AppException.rule("Subjects " + a.subjectName() + " and " + b.subjectName()
                            + " overlap on " + a.examDate() + ".");
                }
            }
        }
    }

    /**
     * Every scheduled subject must have at least one result whose marks have been
     * entered; a subject with no marks at all must not be verifiable.
     */

    /**
     * Tells each candidate their result is out.
     *
     * <p>One message per candidate rather than one per examination: each is keyed on that
     * candidate's own result row, so a republication attempt is recognised as a duplicate
     * instead of arriving twice.
     */
    private void notifyResultsPublished(Examination exam) {
        for (Result result : results.findByExaminationIdOrderByStudentIdAsc(exam.getId())) {
            List<UUID> recipients = communication.recipientsForStudent(result.getStudentId(), null);
            if (recipients.isEmpty()) {
                continue;
            }
            Student student = students.findById(result.getStudentId()).orElse(null);
            Map<String, String> variables = new LinkedHashMap<>();
            variables.put("studentName", student == null ? "Student" : student.displayName());
            variables.put("examName", exam.getName());
            variables.put("percentage", result.getPercentage() == null ? ""
                    : result.getPercentage().toPlainString());
            variables.put("grade", result.getLetterGrade() == null ? "" : result.getLetterGrade());
            variables.put("publishedOn", LocalDate.now().toString());
            events.publishEvent(MessageEvent.of(CommunicationEventCode.RESULT_PUBLISHED,
                    recipients, variables, "Result", result.getId()));
        }
    }

    private boolean hasAllMarksEntered(UUID examinationId) {
        List<ExamSubject> scheduled = subjects.findByExaminationIdOrderBySubjectNameAsc(examinationId);
        if (scheduled.isEmpty()) {
            return false;
        }
        List<Result> all = results.findByExaminationIdOrderByStudentIdAsc(examinationId);
        return scheduled.stream().allMatch(subject -> all.stream()
                .anyMatch(r -> r.getExamSubjectId().equals(subject.getId())
                        && r.getStatus() != Result.ResultStatus.DRAFT));
    }

    private boolean examsAreApproved(UUID examinationId) {
        List<Result> all = results.findByExaminationIdOrderByStudentIdAsc(examinationId);
        return !all.isEmpty() && all.stream().allMatch(r -> r.getStatus() == Result.ResultStatus.APPROVED
                || r.getStatus() == Result.ResultStatus.PUBLISHED);
    }

    private void apply(Examination exam, ExamDtos.ExaminationRequest request) {
        exam.setName(request.name().trim());
        exam.setCode(request.code().trim());
        exam.setAcademicYearId(request.academicYearId());
        exam.setSemesterId(request.semesterId());
        if (request.examType() != null) {
            exam.setExamType(Examination.ExamType.valueOf(request.examType().trim().toUpperCase()));
        }
        exam.setStartDate(request.startDate());
        exam.setEndDate(request.endDate());
        exam.setGradingScaleId(request.gradingScaleId());
        exam.setMaxTotalMarks(request.maxTotalMarks());
        exam.setPassPercentage(request.passPercentage());
    }

    private Examination requireExam(UUID id) {
        return examinations.findById(id).orElseThrow(() -> AppException.notFound("Examination"));
    }
}