package com.educationerp.academic;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Semesters and terms inside an academic year.
 *
 * <p>Semester dates sit inside their academic year and never overlap, because attendance,
 * results and fee instalments are all read against the term a student was enrolled in: two
 * overlapping terms would make "which semester was this?" unanswerable.
 */
@Service
@RequiredArgsConstructor
public class SemesterService {

    private final SemesterRepository semesters;
    private final AcademicYearRepository years;
    private final CourseOfferingRepository offerings;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.SemesterResponse> list(UUID academicYearId) {
        auth.requirePermission("ACADEMIC_READ");
        if (academicYearId == null) {
            return semesters.findAll().stream().map(this::toResponse).toList();
        }
        return semesters.findByAcademicYearIdOrderByOrdinal(academicYearId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AcademicStructureDto.SemesterResponse create(AcademicStructureDto.CreateSemester request) {
        auth.requirePermission("ACADEMIC_CREATE");
        AcademicYear year = years.findById(request.academicYearId())
                .orElseThrow(() -> AppException.notFound("Academic year"));
        if (request.endDate().isBefore(request.startDate())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "A semester cannot end before it starts.");
        }
        boolean startsTooEarly = year.getStartDate() != null
                && request.startDate().isBefore(year.getStartDate());
        boolean endsTooLate = year.getEndDate() != null
                && request.endDate().isAfter(year.getEndDate());
        if (startsTooEarly || endsTooLate) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Semester dates must fall inside academic year " + year.getName() + ".");
        }
        if (semesters.findByAcademicYearIdAndOrdinal(year.getId(), request.ordinal()).isPresent()) {
            throw AppException.duplicate("That academic year already has a term at position "
                    + request.ordinal() + ".");
        }
        for (Semester existing : semesters.findByAcademicYearIdOrderByOrdinal(year.getId())) {
            if (overlaps(request.startDate(), request.endDate(), existing.getStartDate(), existing.getEndDate())) {
                throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                        "Those dates overlap " + existing.getName() + ".");
            }
        }
        Semester semester = new Semester();
        semester.setAcademicYear(year);
        semester.setName(request.name().trim());
        semester.setOrdinal(request.ordinal());
        semester.setStartDate(request.startDate());
        semester.setEndDate(request.endDate());
        semester.setType(request.type() == null ? Semester.TermType.SEMESTER : request.type());
        semester.setStatus(Semester.Status.PLANNED);
        Semester saved = semesters.save(semester);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Semester")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getName())
                .summary("Opened " + saved.getName() + " in " + year.getName())
                .module("ACADEMIC")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Terms are planned, then active, then closed. A term only becomes active when its dates
     * have actually started, and it can only be completed once nothing is still open in it,
     * so results and attendance cannot be filed against a term that has shut.
     */
    @Transactional
    public AcademicStructureDto.SemesterResponse changeStatus(UUID id, Semester.Status target) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Semester semester = requireSemester(id);
        if (semester.getStatus() == target) {
            return toResponse(semester);
        }
        switch (target) {
            case ACTIVE -> {
                if (semester.getStatus() != Semester.Status.PLANNED) {
                    throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                            "Only a planned term can be activated.");
                }
                if (LocalDate.now().isBefore(semester.getStartDate())) {
                    throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            semester.getName() + " does not start until " + semester.getStartDate() + ".");
                }
            }
            case COMPLETED -> {
                if (semester.getStatus() != Semester.Status.ACTIVE) {
                    throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                            "Only an active term can be completed.");
                }
                if (LocalDate.now().isBefore(semester.getEndDate())) {
                    throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                            semester.getName() + " has not finished yet.");
                }
            }
            case PLANNED -> throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A term cannot be reopened once it has been activated or completed.");
            case ARCHIVED -> throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "Archive a completed term from its academic year instead.");
        }
        semester.setStatus(target);
        return toResponse(semesters.save(semester));
    }

    private Semester requireSemester(UUID id) {
        return semesters.findById(id).orElseThrow(() -> AppException.notFound("Semester"));
    }

    private boolean overlaps(LocalDate start, LocalDate end, LocalDate otherStart, LocalDate otherEnd) {
        return !end.isBefore(otherStart) && !start.isAfter(otherEnd);
    }

    private AcademicStructureDto.SemesterResponse toResponse(Semester semester) {
        return new AcademicStructureDto.SemesterResponse(semester.getId(),
                semester.getAcademicYear().getId(), semester.getAcademicYear().getName(),
                semester.getName(), semester.getOrdinal(), semester.getStartDate(),
                semester.getEndDate(), semester.getType(), semester.getStatus(),
                offerings.countBySemesterId(semester.getId()));
    }
}
