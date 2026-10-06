package com.educationerp.student.service;

import com.educationerp.attendance.dto.AttendanceDtos;
import com.educationerp.attendance.service.AttendanceService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.exam.ExamSubjectRepository;
import com.educationerp.exam.ExaminationRepository;
import com.educationerp.exam.ReportCardRepository;
import com.educationerp.exam.ResultRepository;
import com.educationerp.finance.service.FeeAssessmentService;
import com.educationerp.student.dto.StudentDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Student 360: one aggregated read model for the operational student view described in
 * the blueprint. Each section is filled from the module that owns it, so the shape stays
 * stable as later phases add a communication tab.
 */
@Service
@RequiredArgsConstructor
public class Student360Service {

    private final StudentCreationService studentCreationService;
    private final EnrollmentService enrollmentService;
    private final GuardianService guardianService;
    private final AdmissionApplicationService applicationService;
    private final StudentService studentService;
    private final AttendanceService attendanceService;
    private final ResultRepository resultRepository;
    private final ExamSubjectRepository examSubjectRepository;
    private final ExaminationRepository examinationRepository;
    private final ReportCardRepository reportCardRepository;
    private final FeeAssessmentService feeService;
    private final AuthorizationChecker auth;

    @Transactional(readOnly = true)
    public StudentDtos.Student360 get(UUID studentId) {
        auth.requireStudentAccess(studentId);
        var student = studentCreationService.requireStudent(studentId);
        return new StudentDtos.Student360(
                studentCreationService.toResponse(student),
                enrollmentService.forStudent(studentId),
                guardianService.guardiansOf(studentId),
                applicationService.forStudent(studentId),
                student.displayName(),
                attendanceTab(studentId),
                resultsTab(studentId),
                reportCardsTab(studentId),
                feeService.studentSummary(studentId));
    }

    /**
     * Convenience lookup used when staff arrive from a student number rather than an id.
     */
    @Transactional(readOnly = true)
    public StudentDtos.Student360 getByNumber(String studentNumber) {
        return get(studentService.requireIdByNumber(studentNumber));
    }

    private List<StudentDtos.AttendanceSummary> attendanceTab(UUID studentId) {
        AttendanceDtos.SummaryResponse summary = attendanceService.summary(studentId);
        if (summary.totalPeriods() == 0) {
            return List.of();
        }
        return List.of(new StudentDtos.AttendanceSummary(
                summary.attendancePercentage(), summary.totalPeriods(), summary.presentPeriods(),
                summary.absentPeriods(), summary.latePeriods(), summary.excusedPeriods()));
    }

    /**
     * Result lines carry the exam and subject names, which the exam module's own response
     * leaves out; the aggregate view needs them to be readable without a second request.
     */
    private List<StudentDtos.ResultSummary> resultsTab(UUID studentId) {
        return resultRepository.findByStudentId(studentId).stream()
                .map(result -> {
                    var subject = examSubjectRepository.findById(result.getExamSubjectId()).orElse(null);
                    var examination = examinationRepository.findById(result.getExaminationId()).orElse(null);
                    return new StudentDtos.ResultSummary(
                            result.getExamSubjectId(),
                            examination == null ? null : examination.getName(),
                            subject == null ? null : subject.getSubjectName(),
                            subject == null ? null : subject.getSubjectCode(),
                            result.getMarksObtained(), result.getMaxMarks(), result.getPercentage(),
                            result.getLetterGrade(), result.getGradePoint(), result.getPass(),
                            result.getStatus().name());
                })
                .toList();
    }

    private List<StudentDtos.ReportCardSummary> reportCardsTab(UUID studentId) {
        return reportCardRepository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(card -> new StudentDtos.ReportCardSummary(
                        card.getId(), card.getReferenceCode(), card.getTotalMarks(), card.getGpa(),
                        card.getOverallResult(), card.getStatus().name()))
                .toList();
    }
}