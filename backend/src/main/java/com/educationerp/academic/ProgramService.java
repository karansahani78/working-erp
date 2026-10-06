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

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Programmes, the dated versions of them, and the curricula inside those versions.
 *
 * <p>A version exists so that revising a syllabus never rewrites what an existing student
 * was taught: a cohort stays attached to the version it started on, and a new version is
 * opened alongside it rather than edited over the top.
 */
@Service
@RequiredArgsConstructor
public class ProgramService {

    private final ProgramRepository programs;
    private final DepartmentRepository departments;
    private final ProgramVersionRepository versions;
    private final CurriculumRepository curricula;
    private final CurriculumCourseRepository curriculumCourses;
    private final CourseRepository courses;
    private final SemesterRepository semesters;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    // ------------------------------------------------------------------ programmes

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.ProgramResponse> listPrograms(UUID departmentId, Program.Status status) {
        auth.requirePermission("ACADEMIC_READ");
        List<Program> found = departmentId != null
                ? programs.findByDepartmentIdOrderByNameAsc(departmentId)
                : programs.findAll();
        return found.stream()
                .filter(program -> status == null || program.getStatus() == status)
                .map(this::toProgram)
                .toList();
    }

    @Transactional
    public AcademicStructureDto.ProgramResponse createProgram(AcademicStructureDto.CreateProgram request) {
        auth.requirePermission("ACADEMIC_CREATE");
        String code = request.code().trim().toUpperCase(Locale.ROOT);
        if (programs.existsByCodeIgnoreCase(code)) {
            throw AppException.duplicate("A programme with the code " + code + " already exists.");
        }
        Program program = new Program();
        program.setCode(code);
        program.setName(request.name().trim());
        program.setLevel(trimToNull(request.level()));
        program.setDurationSemesters(request.durationSemesters());
        program.setDurationYears(request.durationYears());
        if (request.departmentId() != null) {
            program.setDepartment(departments.findById(request.departmentId())
                    .orElseThrow(() -> AppException.notFound("Department")));
        }
        program.setStatus(Program.Status.ACTIVE);
        Program saved = programs.save(program);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("Program")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getCode())
                .summary("Created programme " + saved.getName())
                .module("ACADEMIC")
                .succeeded(true)
                .build());
        return toProgram(saved);
    }

    /**
     * A programme is retired rather than deleted: enrolments, results and transcripts all
     * point at it, and a hard delete would take that history with it.
     */
    @Transactional
    public AcademicStructureDto.ProgramResponse archiveProgram(UUID id) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Program program = requireProgram(id);
        program.setStatus(Program.Status.ARCHIVED);
        Program saved = programs.save(program);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Program")
                .entityId(saved.getId().toString())
                .entityLabel(saved.getCode())
                .summary("Archived programme " + saved.getName())
                .module("ACADEMIC")
                .succeeded(true)
                .build());
        return toProgram(saved);
    }

    // ----------------------------------------------------------- program versions

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.ProgramVersionResponse> listVersions(UUID programId) {
        auth.requirePermission("ACADEMIC_READ");
        return versions.findByProgramIdOrderByEffectiveFromDesc(programId).stream()
                .map(this::toVersion)
                .toList();
    }

    @Transactional
    public AcademicStructureDto.ProgramVersionResponse createVersion(
            AcademicStructureDto.CreateProgramVersion request) {
        auth.requirePermission("ACADEMIC_CREATE");
        Program program = requireProgram(request.programId());
        String label = request.label().trim();
        if (versions.findByProgramIdAndLabelIgnoreCase(program.getId(), label).isPresent()) {
            throw AppException.duplicate("That programme already has a version labelled " + label + ".");
        }
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A version cannot end before it begins.");
        }
        ProgramVersion version = new ProgramVersion();
        version.setProgram(program);
        version.setLabel(label);
        version.setEffectiveFrom(request.effectiveFrom());
        version.setEffectiveTo(request.effectiveTo());
        version.setTotalCredits(request.totalCredits());
        version.setStatus(ProgramVersion.Status.DRAFT);
        return toVersion(versions.save(version));
    }

    /** Activating a version retires the previous one so only one is ever current. */
    @Transactional
    public AcademicStructureDto.ProgramVersionResponse activateVersion(UUID id) {
        auth.requirePermission("ACADEMIC_UPDATE");
        ProgramVersion version = requireVersion(id);
        for (ProgramVersion other : versions.findByProgramIdOrderByEffectiveFromDesc(version.getProgram().getId())) {
            if (other.getStatus() == ProgramVersion.Status.ACTIVE) {
                other.setStatus(ProgramVersion.Status.RETIRED);
                versions.save(other);
            }
        }
        version.setStatus(ProgramVersion.Status.ACTIVE);
        return toVersion(versions.save(version));
    }

    // ----------------------------------------------------------------- curriculum

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.CurriculumResponse> listCurricula(UUID programVersionId) {
        auth.requirePermission("ACADEMIC_READ");
        return curricula.findByProgramVersionIdAndActiveTrue(programVersionId).stream()
                .map(this::toCurriculum)
                .toList();
    }

    @Transactional
    public AcademicStructureDto.CurriculumResponse createCurriculum(
            AcademicStructureDto.CreateCurriculum request) {
        auth.requirePermission("ACADEMIC_CREATE");
        ProgramVersion version = requireVersion(request.programVersionId());
        String name = request.name().trim();
        if (curricula.findByProgramVersionIdAndNameIgnoreCase(version.getId(), name).isPresent()) {
            throw AppException.duplicate("That programme version already has a curriculum named " + name + ".");
        }
        if (request.effectiveFrom() != null && request.effectiveTo() != null
                && request.effectiveTo().isBefore(request.effectiveFrom())) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "A curriculum cannot end before it begins.");
        }
        Curriculum curriculum = new Curriculum();
        curriculum.setProgramVersion(version);
        curriculum.setName(name);
        curriculum.setDescription(trimToNull(request.description()));
        curriculum.setTotalCredits(request.totalCredits());
        curriculum.setEffectiveFrom(request.effectiveFrom());
        curriculum.setEffectiveTo(request.effectiveTo());
        curriculum.setActive(true);
        return toCurriculum(curricula.save(curriculum));
    }

    @Transactional(readOnly = true)
    public List<AcademicStructureDto.CurriculumCourseResponse> listCurriculumCourses(UUID curriculumId) {
        auth.requirePermission("ACADEMIC_READ");
        requireCurriculum(curriculumId);
        return curriculumCourses.findByCurriculumIdOrderBySemesterOrdinalAscOrdinalAsc(curriculumId).stream()
                .map(this::toCurriculumCourse)
                .toList();
    }

    @Transactional
    public AcademicStructureDto.CurriculumCourseResponse addCurriculumCourse(
            UUID curriculumId, AcademicStructureDto.CurriculumCourseRequest request) {
        auth.requirePermission("ACADEMIC_UPDATE");
        Curriculum curriculum = requireCurriculum(curriculumId);
        Course course = courses.findById(request.courseId())
                .orElseThrow(() -> AppException.notFound("Course"));
        Semester semester = request.semesterId() == null ? null
                : semesters.findById(request.semesterId()).orElseThrow(() -> AppException.notFound("Semester"));
        if (semester != null
                && curriculumCourses.findByCurriculumIdAndSemesterIdAndCourseId(
                        curriculumId, semester.getId(), course.getId()).isPresent()) {
            throw AppException.duplicate("That course is already placed in that semester.");
        }
        CurriculumCourse placement = new CurriculumCourse();
        placement.setCurriculum(curriculum);
        placement.setCourse(course);
        placement.setSemester(semester);
        placement.setRequirementType(request.requirementType() == null
                ? CurriculumCourse.RequirementType.MANDATORY : request.requirementType());
        placement.setCreditHours(request.creditHours() == null ? course.getCreditHours() : request.creditHours());
        placement.setInternalMarks(request.internalMarks() == null ? course.getInternalMarks() : request.internalMarks());
        placement.setExternalMarks(request.externalMarks() == null ? course.getExternalMarks() : request.externalMarks());
        placement.setElectiveGroup(trimToNull(request.electiveGroup()));
        placement.setOrdinal(request.ordinal());
        return toCurriculumCourse(curriculumCourses.save(placement));
    }

    @Transactional
    public void removeCurriculumCourse(UUID curriculumId, UUID curriculumCourseId) {
        auth.requirePermission("ACADEMIC_UPDATE");
        CurriculumCourse placement = curriculumCourses.findById(curriculumCourseId)
                .orElseThrow(() -> AppException.notFound("Curriculum course"));
        if (!placement.getCurriculum().getId().equals(curriculumId)) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That course does not belong to this curriculum.");
        }
        curriculumCourses.delete(placement);
    }

    // -------------------------------------------------------------------- helpers

    private Program requireProgram(UUID id) {
        return programs.findById(id).orElseThrow(() -> AppException.notFound("Program"));
    }

    private ProgramVersion requireVersion(UUID id) {
        return versions.findById(id).orElseThrow(() -> AppException.notFound("Program version"));
    }

    private Curriculum requireCurriculum(UUID id) {
        return curricula.findById(id).orElseThrow(() -> AppException.notFound("Curriculum"));
    }

    private AcademicStructureDto.ProgramResponse toProgram(Program program) {
        return new AcademicStructureDto.ProgramResponse(program.getId(), program.getCode(),
                program.getName(), program.getLevel(), program.getDurationSemesters(),
                program.getDurationYears(),
                program.getDepartment() == null ? null : program.getDepartment().getId(),
                program.getDepartment() == null ? null : program.getDepartment().getName(),
                program.getStatus());
    }

    private AcademicStructureDto.ProgramVersionResponse toVersion(ProgramVersion version) {
        return new AcademicStructureDto.ProgramVersionResponse(version.getId(),
                version.getProgram().getId(), version.getProgram().getCode(), version.getLabel(),
                version.getEffectiveFrom(), version.getEffectiveTo(), version.getTotalCredits(),
                version.getStatus());
    }

    private AcademicStructureDto.CurriculumResponse toCurriculum(Curriculum curriculum) {
        int placed = curriculumCourses.totalCredits(curriculum.getId());
        Integer declared = curriculum.getTotalCredits();
        return new AcademicStructureDto.CurriculumResponse(curriculum.getId(),
                curriculum.getProgramVersion().getId(), curriculum.getProgramVersion().getLabel(),
                curriculum.getName(), curriculum.getDescription(), declared, placed,
                declared == null || declared == placed,
                curriculum.getEffectiveFrom(), curriculum.getEffectiveTo(), curriculum.isActive());
    }

    private AcademicStructureDto.CurriculumCourseResponse toCurriculumCourse(CurriculumCourse placement) {
        Course course = placement.getCourse();
        Semester semester = placement.getSemester();
        Integer internal = placement.getInternalMarks();
        Integer external = placement.getExternalMarks();
        return new AcademicStructureDto.CurriculumCourseResponse(placement.getId(),
                course.getId(), course.getCode(), course.getName(), course.getCreditHours(),
                semester == null ? null : semester.getId(),
                semester == null ? null : semester.getName(),
                semester == null ? null : semester.getOrdinal(),
                placement.getRequirementType(), placement.getCreditHours(), internal, external,
                internal == null || external == null ? null : internal + external,
                placement.getElectiveGroup(), placement.getOrdinal());
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
