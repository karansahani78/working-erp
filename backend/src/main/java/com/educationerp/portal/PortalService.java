package com.educationerp.portal;

import com.educationerp.academic.AcademicYear;
import com.educationerp.academic.AcademicYearRepository;
import com.educationerp.academic.CourseOffering;
import com.educationerp.academic.CourseOfferingRepository;
import com.educationerp.academic.SchoolClass;
import com.educationerp.academic.SchoolClassRepository;
import com.educationerp.academic.Section;
import com.educationerp.academic.SectionRepository;
import com.educationerp.academic.TimetableEntry;
import com.educationerp.academic.TimetableEntryRepository;
import com.educationerp.attendance.AttendanceRecord;
import com.educationerp.attendance.AttendanceRecordRepository;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.common.api.PageResponse;
import com.educationerp.common.error.AppException;
import com.educationerp.communication.CommunicationService;
import com.educationerp.communication.Notice;
import com.educationerp.communication.NoticeRepository;
import com.educationerp.exam.ExamSubject;
import com.educationerp.exam.ExamSubjectRepository;
import com.educationerp.exam.Examination;
import com.educationerp.exam.ExaminationRepository;
import com.educationerp.exam.Result;
import com.educationerp.exam.ResultRepository;
import com.educationerp.finance.StudentFeeAssessment;
import com.educationerp.finance.StudentFeeAssessmentRepository;
import com.educationerp.hr.Employee;
import com.educationerp.hr.EmployeeRepository;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.GuardianRepository;
import com.educationerp.student.Student;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.UserLookup;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * The three portals.
 *
 * <p>Everything here is scoped by who is asking rather than by what permissions they hold. A
 * student sees themselves, a parent sees the children they are linked to, and a teacher sees
 * the classes they are assigned to; there is no path from one portal to another's records
 * because every method starts from the caller's own account and walks outwards from there.
 *
 * <p>Staff work stays in the module endpoints, which are permission-checked. This class
 * deliberately exposes no permission-based reads, so a permission mistake elsewhere cannot
 * turn into a portal leak.
 */
@Service
@RequiredArgsConstructor
public class PortalService {

    private final AuthorizationChecker auth;
    private final UserLookup users;
    private final StudentRepository students;
    private final GuardianRepository guardians;
    private final EnrollmentRepository enrollments;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final AcademicYearRepository years;
    private final AttendanceRecordRepository attendance;
    private final StudentFeeAssessmentRepository assessments;
    private final ResultRepository results;
    private final ExaminationRepository examinations;
    private final ExamSubjectRepository examSubjects;
    private final CourseOfferingRepository offerings;
    private final TimetableEntryRepository timetable;
    private final EmployeeRepository employees;
    private final NoticeRepository notices;
    private final CommunicationService communication;
    private final InstitutionService institutions;

    /** Attendance percentages are over the whole record unless a range is asked for. */
    private static final LocalDate SINCE = LocalDate.of(1970, 1, 1);

    // ------------------------------------------------------------------ student

    @Transactional(readOnly = true)
    public PortalDtos.StudentDashboard studentDashboard() {
        UUID studentId = requireOwnStudent();
        return dashboardFor(studentId);
    }

    @Transactional(readOnly = true)
    public PortalDtos.StudentDashboard studentHome(UUID studentId) {
        auth.requireStudentAccess(studentId);
        return dashboardFor(studentId);
    }

    @Transactional(readOnly = true)
    public PortalDtos.StudentProfile studentProfile() {
        return profileOf(requireStudent(requireOwnStudent()));
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.TimetableRow> studentTimetable() {
        return timetableFor(requireOwnStudent());
    }

    @Transactional(readOnly = true)
    public PortalDtos.AttendanceSummary studentAttendance(LocalDate from, LocalDate to) {
        return attendanceFor(requireOwnStudent(), from, to);
    }

    @Transactional(readOnly = true)
    public PortalDtos.FeeSummary studentFees() {
        return feesFor(requireOwnStudent());
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.PublishedResult> studentResults() {
        return recentResultsFor(requireOwnStudent());
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.CourseRow> studentCourses() {
        return coursesFor(requireOwnStudent());
    }

    private PortalDtos.StudentDashboard dashboardFor(UUID studentId) {
        Student student = requireStudent(studentId);
        return new PortalDtos.StudentDashboard(
                profileOf(student),
                coursesFor(studentId),
                timetableFor(studentId),
                attendanceFor(studentId),
                feesFor(studentId),
                recentResultsFor(studentId),
                noticeRows(Notice.Audience.STUDENTS),
                communication.unreadCount().unread());
    }

    // ------------------------------------------------------------------- parent

    @Transactional(readOnly = true)
    public PortalDtos.ParentDashboard parentDashboard() {
        UUID userId = auth.requireUser().userId();
        Set<UUID> children = guardianScope(userId);
        return new PortalDtos.ParentDashboard(
                auth.requireUser().displayName(),
                children.stream().map(this::childSummary).toList(),
                noticeRows(Notice.Audience.PARENTS),
                communication.unreadCount().unread());
    }

    /**
     * What a parent may see of one child. The child is looked up in the caller's own scope
     * rather than merely checked, so asking for somebody else's child finds nothing to show
     * rather than a record with the details stripped off it.
     */
    @Transactional(readOnly = true)
    public PortalDtos.ChildSummary child(UUID studentId) {
        UUID userId = auth.requireUser().userId();
        if (!guardianScope(userId).contains(studentId)) {
            throw AppException.denied("You are not a guardian of that student.");
        }
        return childSummary(studentId);
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.TimetableRow> childTimetable(UUID studentId) {
        UUID userId = auth.requireUser().userId();
        if (!guardianScope(userId).contains(studentId)) {
            throw AppException.denied("You are not a guardian of that student.");
        }
        return timetableFor(studentId);
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.PublishedResult> childResults(UUID studentId) {
        UUID userId = auth.requireUser().userId();
        if (!guardianScope(userId).contains(studentId)) {
            throw AppException.denied("You are not a guardian of that student.");
        }
        return recentResultsFor(studentId);
    }

    @Transactional(readOnly = true)
    public PortalDtos.AttendanceSummary childAttendance(UUID studentId, LocalDate from, LocalDate to) {
        UUID userId = auth.requireUser().userId();
        if (!guardianScope(userId).contains(studentId)) {
            throw AppException.denied("You are not a guardian of that student.");
        }
        return attendanceFor(studentId, from, to);
    }

    @Transactional(readOnly = true)
    public PortalDtos.FeeSummary childFees(UUID studentId) {
        UUID userId = auth.requireUser().userId();
        if (!guardianScope(userId).contains(studentId)) {
            throw AppException.denied("You are not a guardian of that student.");
        }
        return feesFor(studentId);
    }

    private PortalDtos.ChildSummary childSummary(UUID studentId) {
        Student student = requireStudent(studentId);
        return new PortalDtos.ChildSummary(
                student.getId(),
                student.displayName(),
                student.getStudentNumber(),
                enrolledClassName(studentId),
                attendanceFor(studentId),
                feesFor(studentId),
                communication.unreadCount().unread());
    }

    // ------------------------------------------------------------------ teacher

    @Transactional(readOnly = true)
    public PortalDtos.TeacherDashboard teacherDashboard() {
        Employee teacher = requireOwnTeacher();
        DayOfWeek today = LocalDate.now().getDayOfWeek();
        List<CourseOffering> assigned = assignedOfferings(teacher.getId());
        return new PortalDtos.TeacherDashboard(
                teacher.getId(),
                teacher.fullName(),
                timetableForTeacher(teacher.getId(), today),
                assigned.stream().map(this::assignedClass).toList(),
                pendingMarking(assigned),
                noticeRows(Notice.Audience.TEACHERS),
                communication.unreadCount().unread());
    }

    @Transactional(readOnly = true)
    public List<PortalDtos.TimetableRow> teacherTimetable(LocalDate on) {
        Employee teacher = requireOwnTeacher();
        return timetableForTeacher(teacher.getId(), on.getDayOfWeek());
    }

    /**
     * The students in one of the teacher's own classes.
     *
     * <p>The offering is looked up within the teacher's own assignments first, so a class
     * somebody else teaches is simply not found here. This is the difference between a teacher
     * portal and a staff account with a smaller permission set.
     */
    @Transactional(readOnly = true)
    public List<PortalDtos.AssignedClass> teacherClasses() {
        return assignedOfferings(requireOwnTeacher().getId()).stream()
                .map(this::assignedClass)
                .toList();
    }

    /**
     * The students of one of the teacher's own classes.
     *
     * <p>The offering is looked up within the teacher's own assignments first, so a class
     * somebody else teaches is simply not found here. That is the difference between a teacher
     * portal and a staff account with a smaller permission set.
     */
    @Transactional(readOnly = true)
    public PageResponse<com.educationerp.student.dto.StudentDtos.StudentResponse> teacherStudents(
            UUID courseOfferingId, Pageable pageable) {
        Employee teacher = requireOwnTeacher();
        boolean assigned = assignedOfferings(teacher.getId()).stream()
                .anyMatch(o -> o.getId().equals(courseOfferingId));
        if (!assigned) {
            throw AppException.denied("You are not assigned to that class.");
        }
        CourseOffering offering = offerings.findById(courseOfferingId)
                .orElseThrow(() -> AppException.notFound("Course offering"));
        if (offering.getSection() == null) {
            return PageResponse.from(Page.empty(pageable), this::toStudentResponse);
        }
        List<Student> roster = enrollments
                .findByAcademicYearIdAndSchoolClassIdAndStatusOrderByRollNumberAsc(
                        offering.getAcademicYear().getId(), offering.getSchoolClass().getId(),
                        Enrollment.Status.ACTIVE)
                .stream()
                .filter(enrollment -> enrollment.getSectionId().equals(offering.getSection().getId()))
                .map(enrollment -> requireStudent(enrollment.getStudentId()))
                .toList();
        return PageResponse.from(new PageImpl<>(roster, pageable, roster.size()),
                this::toStudentResponse);
    }

    /**
     * The portal's view of a student row. Deliberately the same shape the student module
     * publishes, so a client rendering a portal list needs no second student type.
     */
    private com.educationerp.student.dto.StudentDtos.StudentResponse toStudentResponse(Student student) {
        return new com.educationerp.student.dto.StudentDtos.StudentResponse(
                student.getId(),
                student.getStudentNumber(),
                student.getUserId(),
                student.getAdmissionId(),
                student.getFirstName(),
                student.getMiddleName(),
                student.getLastName(),
                student.displayName(),
                student.getDateOfBirth(),
                student.getGender(),
                student.getNationality(),
                student.getPhone(),
                student.getEmail(),
                student.getAddress(),
                student.getPhotoUrl(),
                student.getEnrollmentDate(),
                student.getStatus().name(),
                student.getCreatedAt());
    }

    // ------------------------------------------------------------------- shared

    /**
     * The notices for whoever is asking.
     *
     * <p>The audience is worked out from the account rather than passed in, so a student
     * asking this endpoint gets student notices and cannot ask for the staff ones by naming a
     * different audience.
     */
    @Transactional(readOnly = true)
    public List<PortalDtos.NoticeRow> myNotices() {
        UUID userId = auth.requireUser().userId();
        boolean student = users.studentIdOf(userId).isPresent();
        boolean parent = !guardians.studentIdsForUser(userId).isEmpty();
        boolean staff = users.employeeIdOf(userId).isPresent();
        if (student && currentUserHas("PORTAL_STUDENT")) {
            return noticeRows(Notice.Audience.STUDENTS);
        }
        if (parent && currentUserHas("PORTAL_PARENT")) {
            return noticeRows(Notice.Audience.PARENTS);
        }
        if (staff && currentUserHas("PORTAL_TEACHER")) {
            return noticeRows(Notice.Audience.TEACHERS);
        }
        return noticeRows(Notice.Audience.ALL);
    }

    private boolean currentUserHas(String permission) {
        return auth.currentUser() != null && auth.currentUser().permissions().contains(permission);
    }

    // ------------------------------------------------------------------ pieces

    private UUID requireOwnStudent() {
        auth.requirePermission("PORTAL_STUDENT");
        UUID userId = auth.requireUser().userId();
        return users.studentIdOf(userId).orElseThrow(() -> AppException.denied(
                "This portal is for a student account."));
    }

    private Employee requireOwnTeacher() {
        auth.requirePermission("PORTAL_TEACHER");
        UUID userId = auth.requireUser().userId();
        return users.employeeIdOf(userId)
                .flatMap(employees::findById)
                .orElseThrow(() -> AppException.denied("This portal is for a member of staff."));
    }

    /**
     * The children this account may see.
     *
     * <p>Read from the guardian links on every call rather than trusted from the token, so a
     * link removed mid-session takes effect at once: a former guardian's next request finds
     * nothing rather than relying on a session that outlived the relationship.
     */
    private Set<UUID> guardianScope(UUID userId) {
        auth.requirePermission("PORTAL_PARENT");
        return new LinkedHashSet<>(guardians.studentIdsForUser(userId));
    }

    private Student requireStudent(UUID studentId) {
        return students.findById(studentId)
                .orElseThrow(() -> AppException.notFound("Student"));
    }

    private PortalDtos.StudentProfile profileOf(Student student) {
        Enrollment enrollment = enrollments
                .findFirstByStudentIdAndStatus(student.getId(), Enrollment.Status.ACTIVE)
                .orElse(null);
        return new PortalDtos.StudentProfile(
                student.getId(),
                student.getStudentNumber(),
                student.displayName(),
                student.getEmail(),
                student.getPhone(),
                student.getStatus().name(),
                name(enrollment == null ? null : classes.findById(enrollment.getSchoolClassId()).orElse(null)),
                sectionName(enrollment),
                yearName(enrollment),
                enrollment == null ? null : enrollment.getRollNumber());
    }

    private String enrolledClassName(UUID studentId) {
        return enrollments.findFirstByStudentIdAndStatus(studentId, Enrollment.Status.ACTIVE)
                .map(enrollment -> name(classes.findById(enrollment.getSchoolClassId()).orElse(null)))
                .orElse(null);
    }

    private String sectionName(Enrollment enrollment) {
        if (enrollment == null || enrollment.getSectionId() == null) {
            return null;
        }
        return sections.findById(enrollment.getSectionId()).map(Section::getName).orElse(null);
    }

    private String yearName(Enrollment enrollment) {
        if (enrollment == null || enrollment.getAcademicYearId() == null) {
            return null;
        }
        return years.findById(enrollment.getAcademicYearId())
                .map(AcademicYear::getName).orElse(null);
    }

    private String name(SchoolClass schoolClass) {
        return schoolClass == null ? null : schoolClass.getName();
    }

    /**
     * Attendance as a percentage, counting an excused day as present.
     *
     * <p>The figures come from the records themselves rather than from a stored total, so a
     * register that was corrected shows the corrected number.
     */
    private PortalDtos.AttendanceSummary attendanceFor(UUID studentId) {
        return attendanceFor(studentId, null, null);
    }

    private PortalDtos.AttendanceSummary attendanceFor(UUID studentId, LocalDate from, LocalDate to) {
        institutions.requireModuleEnabled(ModuleKey.ATTENDANCE);
        LocalDate start = from == null ? SINCE : from;
        LocalDate end = to == null ? LocalDate.now().plusDays(1) : to;
        List<AttendanceRecord> records = attendance
                .findByStudentIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(studentId, start, end);
        long present = 0;
        long absent = 0;
        long late = 0;
        long excused = 0;
        long accounted = 0;
        for (AttendanceRecord record : records) {
            if (record.getWorkflowStatus() != AttendanceRecord.WorkflowStatus.APPROVED) {
                // A draft register is still the teacher's working copy; it is not the
                // school's record of what happened and must not change a percentage.
                continue;
            }
            switch (record.getStatus()) {
                case PRESENT -> {
                    present++;
                    accounted++;
                }
                case LATE -> {
                    late++;
                    present++;
                    accounted++;
                }
                case ABSENT -> {
                    absent++;
                    accounted++;
                }
                case EXCUSED -> {
                    excused++;
                    present++;
                    accounted++;
                }
            }
        }
        BigDecimal percentage = accounted == 0 ? BigDecimal.ZERO
                : BigDecimal.valueOf(present * 100L)
                        .divide(BigDecimal.valueOf(accounted), 2, RoundingMode.HALF_UP);
        return new PortalDtos.AttendanceSummary(percentage, present, absent, late, excused,
                accounted, start, end);
    }

    /**
     * Fees as one number. Cancelled bills are excluded and refunded money is added back, so
     * "outstanding" is what is genuinely still wanted.
     */
    private PortalDtos.FeeSummary feesFor(UUID studentId) {
        institutions.requireModuleEnabled(ModuleKey.FINANCE);
        List<StudentFeeAssessment> live = assessments.findByStudentIdOrderByCreatedAtDesc(studentId).stream()
                .filter(a -> a.getStatus() != StudentFeeAssessment.Status.CANCELLED)
                .toList();
        BigDecimal billed = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        LocalDate nextDue = null;
        long unpaid = 0;
        for (StudentFeeAssessment assessment : live) {
            billed = billed.add(zero(assessment.getGrossAmount()));
            BigDecimal stillOwed = zero(assessment.getNetAmount())
                    .subtract(zero(assessment.getPaidAmount()))
                    .add(zero(assessment.getRefundedAmount()));
            if (stillOwed.signum() > 0) {
                unpaid++;
                outstanding = outstanding.add(stillOwed);
                if (nextDue == null || assessment.getDueDate().isBefore(nextDue)) {
                    nextDue = assessment.getDueDate();
                }
            }
        }
        BigDecimal paid = billed.subtract(outstanding).max(BigDecimal.ZERO);
        return new PortalDtos.FeeSummary(billed, paid, outstanding, nextDue, unpaid);
    }

    private static BigDecimal zero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** Published and approved results only: a draft mark is nobody else's business. */
    private List<PortalDtos.PublishedResult> recentResultsFor(UUID studentId) {
        institutions.requireModuleEnabled(ModuleKey.EXAMINATION);
        return results.findByStudentId(studentId).stream()
                .filter(result -> result.getStatus() == Result.ResultStatus.PUBLISHED
                        || result.getStatus() == Result.ResultStatus.APPROVED)
                .sorted(Comparator.comparing((Result r) -> r.getCreatedAt()).reversed())
                .limit(20)
                .map(this::publishedResult)
                .toList();
    }

    private PortalDtos.PublishedResult publishedResult(Result result) {
        Examination examination = examinations.findById(result.getExaminationId()).orElse(null);
        ExamSubject subject = examSubjects.findById(result.getExamSubjectId()).orElse(null);
        return new PortalDtos.PublishedResult(
                result.getId(),
                examination == null ? "" : examination.getName(),
                subject == null ? "" : subject.getSubjectName(),
                result.getMarksObtained(),
                result.getMaxMarks(),
                result.getPercentage(),
                result.getLetterGrade(),
                result.getStatus().name(),
                result.getPublishedAt());
    }

    /** The subjects the student is currently enrolled in. */
    private List<PortalDtos.CourseRow> coursesFor(UUID studentId) {
        Enrollment enrollment = enrollments
                .findFirstByStudentIdAndStatus(studentId, Enrollment.Status.ACTIVE)
                .orElse(null);
        if (enrollment == null || enrollment.getSectionId() == null) {
            return List.of();
        }
        return offerings
                .findByAcademicYearIdAndSchoolClassIdAndSectionIdAndActiveTrue(
                        enrollment.getAcademicYearId(), enrollment.getSchoolClassId(),
                        enrollment.getSectionId())
                .stream()
                .map(offering -> new PortalDtos.CourseRow(
                        offering.getId(),
                        offering.getCourse().getCode(),
                        offering.getCourse().getName(),
                        offering.getTeacherName(),
                        offering.getRoom() == null ? null : offering.getRoom().getName(),
                        offering.getTotalMarks(),
                        offering.getSection() == null ? null : offering.getSection().getName()))
                .toList();
    }

    private List<PortalDtos.TimetableRow> timetableFor(UUID studentId) {
        Enrollment enrollment = enrollments
                .findFirstByStudentIdAndStatus(studentId, Enrollment.Status.ACTIVE)
                .orElse(null);
        if (enrollment == null) {
            return List.of();
        }
        return rows(timetable.findForView(null, enrollment.getSchoolClassId(), null, null));
    }

    private List<PortalDtos.TimetableRow> timetableForTeacher(UUID teacherId, DayOfWeek day) {
        return rows(timetable.findForView(null, null, teacherId, day));
    }

    private List<PortalDtos.TimetableRow> rows(List<TimetableEntry> entries) {
        return entries.stream()
                .sorted(Comparator.comparing((TimetableEntry e) -> e.getTimeSlot().getOrdinal() == null
                                ? Integer.MAX_VALUE : e.getTimeSlot().getOrdinal())
                        .thenComparing(e -> e.getTimeSlot().getStartTime()))
                .map(e -> new PortalDtos.TimetableRow(
                        e.getId(),
                        e.getDayOfWeek(),
                        e.getTimeSlot().getStartTime().toString(),
                        e.getTimeSlot().getEndTime().toString(),
                        e.getOffering().getCourse().getCode(),
                        e.getOffering().getCourse().getName(),
                        e.getOffering().getSchoolClass() == null ? null : e.getOffering().getSchoolClass().getName(),
                        e.getOffering().getSection() == null ? null : e.getOffering().getSection().getName(),
                        e.getRoom() == null ? null : e.getRoom().getName(),
                        e.getOffering().getTeacherName()))
                .toList();
    }

    private List<CourseOffering> assignedOfferings(UUID teacherId) {
        return offerings.search(null, null, null, null, null, teacherId, null, Boolean.TRUE,
                        org.springframework.data.domain.Pageable.unpaged())
                .getContent();
    }

    private PortalDtos.AssignedClass assignedClass(CourseOffering offering) {
        Section section = offering.getSection();
        return new PortalDtos.AssignedClass(
                offering.getId(),
                offering.getCourse().getCode(),
                offering.getCourse().getName(),
                offering.getSchoolClass() == null ? null : offering.getSchoolClass().getName(),
                section == null ? null : section.getName(),
                offering.getEnrolledCount(),
                "TEACHER");
    }

    /**
     * Examinations in the teacher's own subjects whose marks are still incomplete, so the
     * teacher opens the portal knowing what is outstanding rather than hunting for it.
     */
    private List<PortalDtos.PendingMarking> pendingMarking(List<CourseOffering> assigned) {
        if (assigned.isEmpty()) {
            return List.of();
        }
        Set<UUID> offeringIds = new LinkedHashSet<>();
        assigned.forEach(o -> offeringIds.add(o.getId()));
        List<PortalDtos.PendingMarking> pending = new java.util.ArrayList<>();
        for (ExamSubject subject : examSubjects.findByCourseOfferingIdIn(offeringIds)) {
            Examination examination = examinations.findById(subject.getExaminationId()).orElse(null);
            if (examination == null || examination.getStatus() == Examination.Status.ARCHIVED) {
                continue;
            }
            long awaiting = results.findByExaminationIdOrderByStudentIdAsc(subject.getExaminationId())
                    .stream()
                    .filter(result -> result.getExamSubjectId().equals(subject.getId()))
                    .filter(result -> result.getStatus() == Result.ResultStatus.DRAFT)
                    .count();
            if (awaiting > 0) {
                pending.add(new PortalDtos.PendingMarking(subject.getId(), examination.getName(),
                        subject.getSubjectName(), awaiting));
            }
        }
        return pending;
    }

    private List<PortalDtos.NoticeRow> noticeRows(Notice.Audience audience) {
        institutions.requireModuleEnabled(ModuleKey.COMMUNICATION);
        return notices.visibleTo(Notice.Status.PUBLISHED, Notice.Audience.ALL, audience, Instant.now())
                .stream()
                .limit(50)
                .map(notice -> new PortalDtos.NoticeRow(
                        notice.getId(),
                        notice.getTitle(),
                        notice.getBody(),
                        PortalDtos.NoticeAudience.valueOf(notice.getAudience().name()),
                        notice.getPublishedAt(),
                        notice.getExpiresAt()))
                .toList();
    }
}