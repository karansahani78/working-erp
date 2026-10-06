package com.educationerp.academic;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.institution.AcademicModel;
import com.educationerp.institution.InstitutionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Course offerings: the real teaching instances students actually enrol in.
 *
 * <p>The blueprint is explicit that a school offering is described by a class and section
 * while a college offering is described by a programme and semester, and that the two must
 * never be offered side by side. Which one applies is decided by the institution's academic
 * model, not by the caller, and the model travels back in the response so a client can render
 * the right fields without guessing.
 */
@Service
@RequiredArgsConstructor
public class CourseOfferingService {

    private final CourseOfferingRepository offerings;
    private final CourseRepository courses;
    private final AcademicYearRepository years;
    private final SemesterRepository semesters;
    private final ProgramRepository programs;
    private final CurriculumRepository curricula;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final RoomRepository rooms;
    private final InstitutionRepository institutions;
    private final AuthorizationChecker auth;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public Page<AcademicStructureDto.CourseOfferingResponse> search(UUID yearId, UUID semesterId,
                                                                   UUID classId, UUID sectionId,
                                                                   UUID programId, UUID teacherId,
                                                                   String term, Boolean active,
                                                                   Pageable pageable) {
        auth.requirePermission("ACADEMIC_READ");
        String search = term == null || term.isBlank() ? null : "%" + term.toLowerCase(Locale.ROOT) + "%";
        return offerings.search(yearId, semesterId, classId, sectionId, programId, teacherId,
                        search, active, pageable)
                .map(this::toResponse);
    }

    @Transactional
    public AcademicStructureDto.CourseOfferingResponse create(AcademicStructureDto.CreateCourseOffering request) {
        auth.requirePermission("ACADEMIC_CREATE");
        AcademicModel model = currentModel();
        Course course = courses.findById(request.courseId()).orElseThrow(() -> AppException.notFound("Course"));
        if (!course.isActive()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Course " + course.getCode() + " is inactive and cannot be taught.");
        }
        AcademicYear year = years.findById(request.academicYearId())
                .orElseThrow(() -> AppException.notFound("Academic year"));

        Semester semester = request.semesterId() == null ? null
                : semesters.findById(request.semesterId()).orElseThrow(() -> AppException.notFound("Semester"));
        SchoolClass schoolClass = request.schoolClassId() == null ? null
                : classes.findById(request.schoolClassId()).orElseThrow(() -> AppException.notFound("School class"));
        Section section = request.sectionId() == null ? null
                : sections.findById(request.sectionId()).orElseThrow(() -> AppException.notFound("Section"));
        Program program = request.programId() == null ? null
                : programs.findById(request.programId()).orElseThrow(() -> AppException.notFound("Program"));
        Curriculum curriculum = request.curriculumId() == null ? null
                : curricula.findById(request.curriculumId()).orElseThrow(() -> AppException.notFound("Curriculum"));
        Room room = request.roomId() == null ? null
                : rooms.findById(request.roomId()).orElseThrow(() -> AppException.notFound("Room"));

        // A section already knows its class, so naming the class is optional.
        if (schoolClass == null && section != null) {
            schoolClass = section.getSchoolClass();
        }

        requireModelApplies(model, semester, schoolClass, section, program);
        if (semester != null && !semester.getAcademicYear().getId().equals(year.getId())) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That term belongs to a different academic year.");
        }
        if (section != null && schoolClass != null
                && !section.getSchoolClass().getId().equals(schoolClass.getId())) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That section does not belong to the chosen class.");
        }
        if (section != null && !section.getSchoolClass().getAcademicYear().getId().equals(year.getId())) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "That section belongs to a different academic year.");
        }

        // The same course taught twice into the same group is a duplicate, not a second
        // section: the blueprint's uniqueness is on course, year, term, class and section.
        if (section != null
                && offerings.findFirstByCourseIdAndAcademicYearIdAndSemesterIdAndSectionId(
                        course.getId(), year.getId(), semester == null ? null : semester.getId(),
                        section.getId()).isPresent()) {
            throw AppException.duplicate(course.getCode() + " is already offered to section "
                    + section.getCode() + " this year.");
        }

        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setAcademicYear(year);
        offering.setSemester(semester);
        offering.setProgram(program);
        offering.setCurriculum(curriculum);
        offering.setSchoolClass(schoolClass);
        offering.setSection(section);
        offering.setActive(true);
        offering.setEnrolledCount(0);
        applyDelivery(offering, request.teacherId(), request.teacherName(), room, request.capacity(),
                request.weeklyPeriods(), request.internalMarks(), request.externalMarks(),
                request.passMarks(), course);

        CourseOffering saved = offerings.save(offering);
        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("CourseOffering")
                .entityId(saved.getId().toString())
                .entityLabel(OfferingCodes.of(saved))
                .summary("Opened offering " + OfferingCodes.of(saved) + " (" + course.getCode() + ")")
                .module("ACADEMIC")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Delivery details are editable while the offering is live. Identity is not: the course,
     * the group it is taught to and the term it runs in are what results and fees are keyed
     * on, so changing one of those would rewrite history.
     */
    @Transactional
    public AcademicStructureDto.CourseOfferingResponse update(
            UUID id, AcademicStructureDto.UpdateCourseOffering request) {
        auth.requirePermission("ACADEMIC_UPDATE");
        CourseOffering offering = requireOffering(id);
        if (!offering.isActive()) {
            throw new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                    "A closed offering cannot be changed. Open a new one instead.");
        }
        Room room = request.roomId() == null ? null
                : rooms.findById(request.roomId()).orElseThrow(() -> AppException.notFound("Room"));
        Integer capacity = request.capacity();
        if (capacity != null && capacity < offering.getEnrolledCount()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Capacity cannot drop below the " + offering.getEnrolledCount()
                            + " student(s) already enrolled.");
        }
        applyDelivery(offering, request.teacherId(), request.teacherName(), room, capacity,
                request.weeklyPeriods(), request.internalMarks(), request.externalMarks(),
                request.passMarks(), offering.getCourse());
        return toResponse(offerings.save(offering));
    }

    @Transactional
    public AcademicStructureDto.CourseOfferingResponse close(UUID id) {
        auth.requirePermission("ACADEMIC_UPDATE");
        CourseOffering offering = requireOffering(id);
        offering.setActive(false);
        CourseOffering saved = offerings.save(offering);
        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("CourseOffering")
                .entityId(saved.getId().toString())
                .entityLabel(OfferingCodes.of(saved))
                .summary("Closed offering " + OfferingCodes.of(saved))
                .module("ACADEMIC")
                .succeeded(true)
                .build());
        return toResponse(saved);
    }

    /**
     * Marks are checked here rather than left to the exam module: a pass mark above the total
     * makes every student fail, and a total below the internal plus external split means the
     * paper does not add up.
     */
    private void applyDelivery(CourseOffering offering, UUID teacherId, String teacherName, Room room,
                               Integer capacity, Integer weeklyPeriods, Integer internalMarks,
                               Integer externalMarks, Integer passMarks, Course course) {
        if (room != null && !room.isActive()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "Room " + room.getCode() + " is inactive.");
        }
        if (room != null && capacity != null && room.getCapacity() != null
                && capacity > room.getCapacity()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    room.getName() + " holds " + room.getCapacity() + ", so it cannot take "
                            + capacity + " students.");
        }
        if (capacity != null && capacity <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR, "Capacity must be greater than zero.");
        }
        Integer internal = internalMarks == null ? course.getInternalMarks() : internalMarks;
        Integer external = externalMarks == null ? course.getExternalMarks() : externalMarks;
        int total = (internal == null ? 0 : internal) + (external == null ? 0 : external);
        if (total <= 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "An offering needs internal and external marks that add up to more than zero.");
        }
        // A course carries no pass mark of its own, so an offering that does not name one
        // simply has none; the exam module may still decide pass by percentage.
        Integer pass = passMarks;
        if (pass != null && (pass <= 0 || pass > total)) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "The pass mark must be between 1 and " + total + ".");
        }
        offering.setTeacherId(teacherId);
        offering.setTeacherName(teacherName == null || teacherName.isBlank() ? null : teacherName.trim());
        offering.setRoom(room);
        offering.setCapacity(capacity);
        // The column is not nullable: an offering that has not been timetabled yet simply
        // has no periods of its own.
        if (weeklyPeriods != null && weeklyPeriods < 0) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "Weekly periods cannot be negative.");
        }
        offering.setWeeklyPeriods(weeklyPeriods == null ? 0 : weeklyPeriods);
        offering.setInternalMarks(internal);
        offering.setExternalMarks(external);
        offering.setTotalMarks(total);
        offering.setPassMarks(pass);
    }

    /**
     * Enforces the blueprint's rule that school and college structures are not mixed. A
     * hybrid institution accepts either, but never both on the same offering.
     */
    private void requireModelApplies(AcademicModel model, Semester semester, SchoolClass schoolClass,
                                     Section section, Program program) {
        boolean schoolSide = schoolClass != null || section != null;
        boolean collegeSide = semester != null || program != null;
        if (schoolSide && collegeSide) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "An offering is either school-based (class and section) or college-based "
                            + "(programme and term), never both.");
        }
        if (!schoolSide && !collegeSide) {
            throw new AppException(ErrorCode.VALIDATION_ERROR,
                    "An offering needs either a class and section or a programme and term.");
        }
        if (schoolSide && !model.usesClassSectionStructure()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This is a " + model.name().toLowerCase(Locale.ROOT)
                            + ": an offering needs a programme and a term, not a class and section.");
        }
        if (collegeSide && !model.usesFacultyDepartmentProgram()) {
            throw new AppException(ErrorCode.BUSINESS_RULE_VIOLATION,
                    "This is a school: an offering needs a class and a section, "
                            + "not a programme and term.");
        }
    }

    private AcademicModel currentModel() {
        return institutions.findFirstByOrderByCreatedAtAsc()
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_STATE_TRANSITION,
                        "The institution has not been set up yet."))
                .getAcademicModel();
    }

    private CourseOffering requireOffering(UUID id) {
        return offerings.findById(id).orElseThrow(() -> AppException.notFound("Course offering"));
    }

    private AcademicStructureDto.CourseOfferingResponse toResponse(CourseOffering offering) {
        Integer capacity = offering.getCapacity();
        int enrolled = offering.getEnrolledCount() == null ? 0 : offering.getEnrolledCount();
        return new AcademicStructureDto.CourseOfferingResponse(offering.getId(),
                OfferingCodes.of(offering), currentModel(),
                offering.getCourse().getId(), offering.getCourse().getCode(), offering.getCourse().getName(),
                offering.getAcademicYear().getId(), offering.getAcademicYear().getName(),
                offering.getSemester() == null ? null : offering.getSemester().getId(),
                offering.getSemester() == null ? null : offering.getSemester().getName(),
                offering.getProgram() == null ? null : offering.getProgram().getId(),
                offering.getProgram() == null ? null : offering.getProgram().getName(),
                offering.getCurriculum() == null ? null : offering.getCurriculum().getId(),
                offering.getSchoolClass() == null ? null : offering.getSchoolClass().getId(),
                offering.getSchoolClass() == null ? null : offering.getSchoolClass().getName(),
                offering.getSection() == null ? null : offering.getSection().getId(),
                offering.getSection() == null ? null : offering.getSection().getName(),
                offering.getTeacherId(), offering.getTeacherName(),
                offering.getRoom() == null ? null : offering.getRoom().getId(),
                offering.getRoom() == null ? null : offering.getRoom().getName(),
                capacity, enrolled, capacity == null ? null : capacity - enrolled,
                offering.getWeeklyPeriods(), offering.getInternalMarks(), offering.getExternalMarks(),
                offering.getTotalMarks(), offering.getPassMarks(), offering.isActive());
    }
}
