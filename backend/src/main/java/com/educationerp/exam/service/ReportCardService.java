package com.educationerp.exam.service;

import com.educationerp.attendance.AttendanceRecordRepository;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.exam.ExamSubject;
import com.educationerp.exam.ExamSubjectRepository;
import com.educationerp.exam.Examination;
import com.educationerp.exam.ExaminationRepository;
import com.educationerp.exam.ReportCard;
import com.educationerp.exam.ReportCardItem;
import com.educationerp.exam.ReportCardItemRepository;
import com.educationerp.exam.ReportCardRepository;
import com.educationerp.exam.Result;
import com.educationerp.exam.ResultRepository;
import com.educationerp.exam.Transcript;
import com.educationerp.exam.TranscriptRepository;
import com.educationerp.exam.dto.ExamDtos;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import com.educationerp.academic.CourseOfferingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Report cards and transcripts.
 *
 * <p>A report card is a snapshot: subject marks, grades and totals are copied onto it when
 * it is generated, so a later correction to a result cannot silently rewrite a card that
 * was already handed to a family. Transcripts aggregate every report card for cumulative
 * GPA/CGPA.
 */
@Service
@RequiredArgsConstructor
public class ReportCardService {

    private final ReportCardRepository reportCards;
    private final ReportCardItemRepository reportCardItems;
    private final TranscriptRepository transcripts;
    private final ResultRepository results;
    private final ExamSubjectRepository subjects;
    private final ExaminationRepository examinations;
    private final EnrollmentRepository enrollments;
    private final CourseOfferingRepository offerings;
    private final StudentRepository students;
    private final AttendanceRecordRepository attendance;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    /**
     * Generates (or regenerates) the report card for a student in an academic year and
     * semester from the results of every examination matching that period.
     */
    @Transactional
    public ExamDtos.ReportCardResponse generate(UUID studentId, UUID academicYearId, UUID semesterId) {
        auth.requirePermission("REPORT_CARD_GENERATE");
        Student student = students.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student"));

        List<Result> studentResults = results.findByStudentId(studentId).stream()
                .filter(r -> r.getStatus() == Result.ResultStatus.APPROVED
                        || r.getStatus() == Result.ResultStatus.PUBLISHED)
                .filter(r -> matchesPeriod(r, academicYearId, semesterId))
                .toList();
        if (studentResults.isEmpty()) {
            throw AppException.rule("This student has no approved results for the selected period.");
        }

        ReportCard card = reportCards
                .findByStudentIdAndAcademicYearIdAndSemesterId(studentId, academicYearId, semesterId)
                .orElseGet(() -> {
                    ReportCard fresh = new ReportCard();
                    fresh.setStudentId(studentId);
                    fresh.setAcademicYearId(academicYearId);
                    fresh.setSemesterId(semesterId);
                    fresh.setReferenceCode(nextReference("RC"));
                    fresh.setStatus(ReportCard.Status.DRAFT);
                    return fresh;
                });
        if (card.getStatus() == ReportCard.Status.PUBLISHED) {
            throw AppException.rule("This report card has already been published and cannot be regenerated.");
        }
        // Persist first so the items below can reference the card's id.
        reportCards.save(card);
        reportCardItems.deleteAll(reportCardItems.findByReportCardIdOrderBySubjectNameAsc(card.getId()));

        List<ReportCardItem> items = new ArrayList<>();
        BigDecimal totalMarks = BigDecimal.ZERO;
        BigDecimal creditsEarned = BigDecimal.ZERO;
        BigDecimal weightedPoints = BigDecimal.ZERO;
        boolean allPass = true;

        // One line per subject: a student sitting the same paper twice keeps the latest.
        Map<UUID, Result> latestBySubject = new LinkedHashMap<>();
        for (Result result : studentResults) {
            latestBySubject.put(result.getExamSubjectId(), result);
        }

        for (Result result : latestBySubject.values()) {
            ExamSubject subject = subjects.findById(result.getExamSubjectId()).orElse(null);
            BigDecimal credits = creditsFor(result, subject);

            ReportCardItem item = new ReportCardItem();
            item.setReportCardId(card.getId());
            item.setExamSubjectId(result.getExamSubjectId());
            item.setSubjectName(subject == null ? "Subject" : subject.getSubjectName());
            item.setSubjectCode(subject == null ? null : subject.getSubjectCode());
            item.setMarksObtained(result.getMarksObtained());
            item.setMaxMarks(result.getMaxMarks());
            item.setCredits(credits);
            item.setLetterGrade(result.getLetterGrade());
            item.setGradePoint(result.getGradePoint());
            item.setPass(result.getPass());
            items.add(item);

            totalMarks = totalMarks.add(nz(result.getMarksObtained()));
            if (!Boolean.TRUE.equals(result.getPass())) {
                allPass = false;
            }
            if (credits != null && credits.compareTo(BigDecimal.ZERO) > 0
                    && result.getGradePoint() != null) {
                creditsEarned = creditsEarned.add(credits);
                weightedPoints = weightedPoints.add(result.getGradePoint().multiply(credits));
            }
        }
        reportCardItems.saveAll(items);

        card.setTotalMarks(totalMarks);
        card.setTotalCredits(creditsEarned);
        card.setGpa(weightedPoints.divide(creditsEarned, 2, RoundingMode.HALF_UP));
        card.setAttendancePercentage(attendancePercentage(studentId));
        card.setOverallResult(allPass ? "PASS" : "FAIL");
        card.setStatus(ReportCard.Status.GENERATED);
        card.setEnrollmentId(latestEnrollmentId(studentId, academicYearId));
        reportCards.save(card);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("ReportCard")
                .entityId(card.getId().toString())
                .entityLabel(student.getStudentNumber())
                .summary("Generated report card with " + items.size() + " subjects")
                .after(Map.of("overallResult", card.getOverallResult(),
                        "totalMarks", String.valueOf(card.getTotalMarks())))
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return toResponse(card);
    }

    @Transactional
    public ExamDtos.ReportCardResponse transition(UUID cardId, ExamDtos.StatusRequest request) {
        auth.requirePermission("REPORT_CARD_GENERATE");
        ReportCard card = reportCards.findById(cardId)
                .orElseThrow(() -> AppException.notFound("Report card"));
        requireStudentScope(card.getStudentId());
        ReportCard.Status current = card.getStatus();
        ReportCard.Status target;
        try {
            target = ReportCard.Status.valueOf(request.status().trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Unknown report card status.");
        }
        boolean allowed = switch (current) {
            case DRAFT, GENERATED -> target == ReportCard.Status.GENERATED
                    || target == ReportCard.Status.APPROVED;
            case APPROVED -> target == ReportCard.Status.PUBLISHED;
            case PUBLISHED -> false;
        };
        if (!allowed) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A report card in " + current + " cannot move to " + target + ".");
        }
        if (target == ReportCard.Status.APPROVED
                && reportCardItems.findByReportCardIdOrderBySubjectNameAsc(cardId).isEmpty()) {
            throw AppException.rule("An empty report card cannot be approved.");
        }
        card.setStatus(target);
        if (target == ReportCard.Status.PUBLISHED) {
            card.setPublishedAt(Instant.now());
        }
        reportCards.save(card);
        return toResponse(card);
    }

    @Transactional(readOnly = true)
    public ExamDtos.ReportCardResponse get(UUID cardId) {
        auth.requirePermission("REPORT_CARD_READ");
        ReportCard card = reportCards.findById(cardId)
                .orElseThrow(() -> AppException.notFound("Report card"));
        requireStudentScope(card.getStudentId());
        return toResponse(card);
    }

    @Transactional(readOnly = true)
    public List<ExamDtos.ReportCardResponse> forStudent(UUID studentId) {
        auth.requireStudentAccess(studentId);
        return reportCards.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ---------- Transcript ----------

    /** Builds a cumulative transcript across every generated report card for a student. */
    @Transactional
    public ExamDtos.TranscriptResponse generateTranscript(UUID studentId) {
        auth.requirePermission("TRANSCRIPT_GENERATE");
        Student student = students.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student"));
        List<ReportCard> cards = reportCards.findByStudentIdOrderByCreatedAtDesc(studentId);
        if (cards.isEmpty()) {
            throw AppException.rule("This student has no report cards to build a transcript from.");
        }

        BigDecimal totalCredits = BigDecimal.ZERO;
        BigDecimal weightedPoints = BigDecimal.ZERO;
        for (ReportCard card : cards) {
            if (card.getTotalCredits() != null && card.getTotalCredits().compareTo(BigDecimal.ZERO) > 0) {
                totalCredits = totalCredits.add(card.getTotalCredits());
                weightedPoints = weightedPoints.add(
                        nz(card.getGpa()).multiply(card.getTotalCredits()));
            }
        }

        Transcript transcript = new Transcript();
        transcript.setStudentId(studentId);
        transcript.setReferenceCode(nextReference("TR"));
        transcript.setGeneratedBy(currentUserId());
        transcript.setGeneratedAt(Instant.now());
        transcript.setTotalCredits(totalCredits);
        transcript.setCumulativeGpa(weightedPoints.divide(totalCredits, 2, RoundingMode.HALF_UP));
        transcript.setStatus(Transcript.Status.DRAFT);
        transcripts.save(transcript);

        audit.record(AuditEvent.builder()
                .action(com.educationerp.audit.AuditAction.UPDATE)
                .entityType("Transcript")
                .entityId(transcript.getId().toString())
                .entityLabel(student.getStudentNumber())
                .summary("Generated transcript across " + cards.size() + " report cards")
                .module("EXAMINATION")
                .succeeded(true)
                .build());

        return toResponse(transcript, cards);
    }

    @Transactional(readOnly = true)
    public ExamDtos.TranscriptResponse getTranscript(UUID transcriptId) {
        auth.requirePermission("TRANSCRIPT_READ");
        Transcript transcript = transcripts.findById(transcriptId)
                .orElseThrow(() -> AppException.notFound("Transcript"));
        requireStudentScope(transcript.getStudentId());
        return toResponse(transcript, reportCards.findByStudentIdOrderByCreatedAtDesc(transcript.getStudentId()));
    }

    @Transactional
    public ExamDtos.TranscriptResponse finaliseTranscript(UUID transcriptId) {
        auth.requirePermission("TRANSCRIPT_GENERATE");
        Transcript transcript = transcripts.findById(transcriptId)
                .orElseThrow(() -> AppException.notFound("Transcript"));
        requireStudentScope(transcript.getStudentId());
        if (transcript.getStatus() != Transcript.Status.DRAFT) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Only a draft transcript can be finalised.");
        }
        transcript.setStatus(Transcript.Status.FINALISED);
        transcript.setFinalisedAt(Instant.now());
        transcripts.save(transcript);
        return toResponse(transcript, reportCards.findByStudentIdOrderByCreatedAtDesc(transcript.getStudentId()));
    }

    // ---------- helpers ----------

    private boolean matchesPeriod(Result result, UUID academicYearId, UUID semesterId) {
        return examinations.findById(result.getExaminationId())
                .map(exam -> (academicYearId == null || academicYearId.equals(exam.getAcademicYearId()))
                        && (semesterId == null || semesterId.equals(exam.getSemesterId())))
                .orElse(false);
    }

    /**
     * Credit weight for a grade point. Enrolment carries no credit value, so the weight is
     * read from the exam subject's course offering when one is linked, defaulting to one so
     * an unlinked subject still contributes to the average.
     */
    private BigDecimal creditsFor(Result result, ExamSubject subject) {
        if (subject != null && subject.getCourseOfferingId() != null) {
            Integer creditHours = offerings.findById(subject.getCourseOfferingId())
                    .map(offering -> offering.getCourse().getCreditHours())
                    .orElse(null);
            if (creditHours != null && creditHours > 0) {
                return BigDecimal.valueOf(creditHours);
            }
        }
        return BigDecimal.ONE;
    }

    private BigDecimal attendancePercentage(UUID studentId) {
        var counts = attendance.countByStatus(studentId, LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31));
        long total = counts.stream().mapToLong(AttendanceRecordRepository.StatusCount::getTotal).sum();
        if (total == 0) {
            return null;
        }
        long attended = counts.stream()
                .filter(c -> c.getStatus().countsAsPresent())
                .mapToLong(AttendanceRecordRepository.StatusCount::getTotal)
                .sum();
        return BigDecimal.valueOf(attended)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }

    private UUID latestEnrollmentId(UUID studentId, UUID academicYearId) {
        return enrollments.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(e -> e.getStatus() == Enrollment.Status.ACTIVE)
                .filter(e -> academicYearId == null || academicYearId.equals(e.getAcademicYearId()))
                .map(Enrollment::getId)
                .findFirst()
                .orElse(null);
    }

    private ExamDtos.ReportCardResponse toResponse(ReportCard card) {
        List<ExamDtos.ReportCardItemResponse> items = reportCardItems
                .findByReportCardIdOrderBySubjectNameAsc(card.getId()).stream()
                .map(ExamDtos.ReportCardItemResponse::from)
                .toList();
        return new ExamDtos.ReportCardResponse(card.getId(), card.getStudentId(), card.getAcademicYearId(),
                card.getSemesterId(), card.getReferenceCode(), card.getTotalMarks(), card.getTotalCredits(),
                card.getGpa(), card.getCgpa(), card.getAttendancePercentage(), card.getOverallResult(),
                card.getStatus().name(), card.getPublishedAt(), items);
    }

    private ExamDtos.TranscriptResponse toResponse(Transcript transcript, List<ReportCard> cards) {
        return new ExamDtos.TranscriptResponse(transcript.getId(), transcript.getStudentId(),
                transcript.getReferenceCode(), transcript.getGeneratedAt(), transcript.getTotalCredits(),
                transcript.getCumulativeGpa(), transcript.getCumulativeCgpa(), transcript.getStatus().name(),
                transcript.getFinalisedAt(), cards.stream().map(this::toResponse).toList());
    }

    private String nextReference(String prefix) {
        String base = prefix + "-" + java.time.Year.now().getValue() + "-";
        String reference;
        long sequence;
        do {
            sequence = (long) (Math.random() * 900_000) + 100_000;
            reference = base + sequence;
        } while (reportCards.findByReferenceCodeIgnoreCase(reference).isPresent()
                || transcripts.findByReferenceCodeIgnoreCase(reference).isPresent());
        return reference;
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * A report card or transcript belongs to one student. Staff who read them generally
     * also hold STUDENT_READ, but a school may issue a custom role that reads cards
     * without the blanket student-data read, so the owner is always enforced.
     */
    private void requireStudentScope(UUID studentId) {
        auth.requireStudentAccess(studentId);
    }

    private UUID currentUserId() {
        var user = auth.currentUser();
        return user == null ? null : user.userId();
    }
}