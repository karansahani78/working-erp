package com.educationerp.student.service;

import com.educationerp.academic.AcademicYearRepository;
import com.educationerp.academic.SchoolClassRepository;
import com.educationerp.academic.SectionRepository;
import com.educationerp.academic.SemesterRepository;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.Student;
import com.educationerp.student.dto.StudentDtos;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Student enrolment into classes and sections.
 *
 * <p>Integrity rules from the blueprint are enforced here: no duplicate enrolment, no
 * enrolment into an inactive class, and no enrolling a student who is not in a state that
 * permits it. Section capacity is respected when the section defines one.
 */
@Service
@RequiredArgsConstructor
public class EnrollmentService {

    private final EnrollmentRepository repository;
    private final StudentCreationService studentCreationService;
    private final AcademicYearRepository yearRepository;
    private final SemesterRepository semesterRepository;
    private final SchoolClassRepository classRepository;
    private final SectionRepository sectionRepository;
    private final AuthorizationChecker auth;

    @Transactional
    public StudentDtos.EnrollmentResponse enroll(StudentDtos.EnrollmentRequest request) {
        auth.requirePermission("ENROLLMENT_CREATE");
        Student student = studentCreationService.requireStudent(request.studentId());
        if (student.getStatus() != Student.Status.ACTIVE && student.getStatus() != Student.Status.APPLICANT) {
            throw AppException.rule("A student with status " + student.getStatus() + " cannot be enrolled.");
        }
        if (request.academicYearId() == null) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "An academic year is required to enrol a student.");
        }
        yearRepository.findById(request.academicYearId())
                .orElseThrow(() -> AppException.notFound("Academic year"));

        if (request.schoolClassId() != null) {
            var schoolClass = classRepository.findById(request.schoolClassId())
                    .orElseThrow(() -> AppException.notFound("Class"));
            if (!schoolClass.isActive()) {
                throw AppException.rule("Students cannot be enrolled into an inactive class.");
            }
            if (!schoolClass.getAcademicYear().getId().equals(request.academicYearId())) {
                throw AppException.rule("The selected class belongs to a different academic year.");
            }
        }
        if (request.semesterId() != null) {
            var semester = semesterRepository.findById(request.semesterId())
                    .orElseThrow(() -> AppException.notFound("Semester"));
            if (!semester.getAcademicYear().getId().equals(request.academicYearId())) {
                throw AppException.rule("The selected semester belongs to a different academic year.");
            }
        }
        if (request.sectionId() != null) {
            var section = sectionRepository.findById(request.sectionId())
                    .orElseThrow(() -> AppException.notFound("Section"));
            if (!section.isActive()) {
                throw AppException.rule("Students cannot be enrolled into an inactive section.");
            }
            if (request.schoolClassId() != null
                    && !section.getSchoolClass().getId().equals(request.schoolClassId())) {
                throw AppException.rule("The selected section does not belong to the selected class.");
            }
            checkCapacity(section.getId(), section.getCapacity(), student.getId());
        }
        if (request.schoolClassId() != null
                && repository.existsByStudentIdAndAcademicYearIdAndSemesterIdAndSchoolClassId(
                student.getId(), request.academicYearId(), request.semesterId(), request.schoolClassId())) {
            throw AppException.duplicate("This student is already enrolled in that class.");
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(student.getId());
        enrollment.setAcademicYearId(request.academicYearId());
        enrollment.setSemesterId(request.semesterId());
        enrollment.setSchoolClassId(request.schoolClassId());
        enrollment.setSectionId(request.sectionId());
        enrollment.setRollNumber(request.rollNumber());
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        return toResponse(repository.save(enrollment));
    }

    @Transactional(readOnly = true)
    public List<StudentDtos.EnrollmentResponse> forStudent(UUID studentId) {
        auth.requireStudentAccess(studentId);
        return repository.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public StudentDtos.EnrollmentResponse get(UUID id) {
        auth.requirePermission("ENROLLMENT_READ");
        return toResponse(require(id));
    }

    @Transactional
    public StudentDtos.EnrollmentResponse update(UUID id, StudentDtos.EnrollmentRequest request) {
        auth.requirePermission("ENROLLMENT_UPDATE");
        Enrollment enrollment = require(id);
        if (request.sectionId() != null) {
            var section = sectionRepository.findById(request.sectionId())
                    .orElseThrow(() -> AppException.notFound("Section"));
            checkCapacity(section.getId(), section.getCapacity(), enrollment.getStudentId());
            enrollment.setSectionId(section.getId());
        }
        if (request.rollNumber() != null) {
            enrollment.setRollNumber(request.rollNumber());
        }
        return toResponse(repository.save(enrollment));
    }

    @Transactional
    public StudentDtos.EnrollmentResponse changeStatus(UUID id, String status) {
        auth.requirePermission("ENROLLMENT_UPDATE");
        Enrollment enrollment = require(id);
        try {
            enrollment.setStatus(Enrollment.Status.valueOf(status.trim().toUpperCase(java.util.Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            throw new AppException(com.educationerp.common.error.ErrorCode.VALIDATION_ERROR,
                    "Unknown enrolment status: " + status);
        }
        return toResponse(repository.save(enrollment));
    }

    @Transactional
    public void delete(UUID id) {
        auth.requirePermission("ENROLLMENT_DELETE");
        repository.delete(require(id));
    }

    /**
     * Guards against over-filling a section. The current occupant is excluded so moving a
     * student between sections does not trip their own count.
     */
    private void checkCapacity(UUID sectionId, Integer capacity, UUID studentId) {
        if (capacity == null || capacity <= 0) {
            return;
        }
        long occupied = repository.countBySectionIdAndStatus(sectionId, Enrollment.Status.ACTIVE);
        boolean alreadyInSection = repository
                .findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .anyMatch(e -> e.getSectionId() != null && e.getSectionId().equals(sectionId));
        if (!alreadyInSection && occupied >= capacity) {
            throw AppException.rule("This section is already at its capacity of " + capacity + ".");
        }
    }

    Enrollment require(UUID id) {
        return repository.findById(id).orElseThrow(() -> AppException.notFound("Enrolment"));
    }

    public StudentDtos.EnrollmentResponse toResponse(Enrollment enrollment) {
        return new StudentDtos.EnrollmentResponse(
                enrollment.getId(),
                enrollment.getStudentId(),
                enrollment.getAcademicYearId(),
                enrollment.getSemesterId(),
                enrollment.getSchoolClassId(),
                enrollment.getSectionId(),
                enrollment.getRollNumber(),
                enrollment.getStatus().name(),
                enrollment.getEnrolledAt());
    }
}