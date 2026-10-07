package com.educationerp.support;

import com.educationerp.academic.AcademicYear;
import com.educationerp.academic.Course;
import com.educationerp.academic.CourseOffering;
import com.educationerp.academic.CourseOfferingRepository;
import com.educationerp.academic.CourseRepository;
import com.educationerp.academic.SchoolClass;
import com.educationerp.academic.Section;
import com.educationerp.academic.SchoolClassRepository;
import com.educationerp.academic.SectionRepository;
import com.educationerp.academic.Semester;
import com.educationerp.academic.SemesterRepository;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.hr.Employee;
import com.educationerp.hr.EmployeeRepository;
import com.educationerp.student.Student;
import com.educationerp.student.service.StudentCreationService;
import com.educationerp.student.StudentRepository;
import com.educationerp.student.Enrollment;
import com.educationerp.student.EnrollmentRepository;
import com.educationerp.student.dto.StudentDtos;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Builds the academic and student rows that attendance and examination tests need.
 *
 * <p>Seeded through the repositories rather than the API: these tests care about
 * attendance and results behaviour, and building a whole class graph over HTTP in every
 * test would obscure that.
 */
@Component
public class CourseOfferingFixture {

    private final CourseRepository courses;
    private final SchoolClassRepository classes;
    private final SectionRepository sections;
    private final SemesterRepository semesters;
    private final CourseOfferingRepository offerings;
    private final StudentRepository students;
    private final StudentCreationService studentCreation;
    private final EmployeeRepository employees;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final TestData testData;

    private final AtomicInteger studentCounter = new AtomicInteger();
    private final AtomicInteger employeeCounter = new AtomicInteger();

    public CourseOfferingFixture(CourseRepository courses,
                                 SchoolClassRepository classes,
                                 SectionRepository sections,
                                 SemesterRepository semesters,
                                 CourseOfferingRepository offerings,
                                 StudentRepository students,
                                 StudentCreationService studentCreation,
                                 EmployeeRepository employees,
                                 UserRepository users,
                                 EnrollmentRepository enrollments,
                                 TestData testData) {
        this.courses = courses;
        this.classes = classes;
        this.sections = sections;
        this.semesters = semesters;
        this.offerings = offerings;
        this.students = students;
        this.studentCreation = studentCreation;
        this.employees = employees;
        this.users = users;
        this.enrollments = enrollments;
        this.testData = testData;
    }

    /** A course offering for the first seeded class, creating the course if needed. */
    public UUID createOffering() {
        AcademicYear year = testData.academicYear();
        SchoolClass schoolClass = classes.findAll().stream()
                .filter(c -> c.getAcademicYear() != null && c.getAcademicYear().getId().equals(year.getId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No seeded class in the current academic year"));

        Course course = courses.findByCodeIgnoreCase("ENG-101").orElseGet(() -> {
            Course created = new Course();
            created.setCode("ENG-101");
            created.setName("English");
            created.setCreditHours(3);
            created.setWeeklyHours(4);
            created.setActive(true);
            return courses.save(created);
        });

        Section section = sections.findBySchoolClassIdAndActiveTrueOrderByCodeAsc(schoolClass.getId()).stream()
                .findFirst()
                .orElse(null);

        CourseOffering offering = new CourseOffering();
        offering.setCourse(course);
        offering.setAcademicYear(year);
        offering.setSchoolClass(schoolClass);
        offering.setSection(section);
        offering.setCapacity(40);
        offering.setWeeklyPeriods(4);
        offering.setTotalMarks(100);
        offering.setPassMarks(40);
        offerings.save(offering);
        return offering.getId();
    }

    /** An offering with a named teacher, for tests of teacher-scoped behaviour. */
    public UUID createOffering(UUID teacherId) {
        UUID offeringId = createOffering();
        assignTeacher(offeringId, teacherId);
        return offeringId;
    }

    public void assignTeacher(UUID offeringId, UUID employeeId) {
        CourseOffering offering = offerings.findById(offeringId)
                .orElseThrow(() -> new IllegalStateException("Offering not found"));
        offering.setTeacherId(employeeId);
        employees.findById(employeeId)
                .ifPresent(employee -> offering.setTeacherName(employee.fullName()));
        offerings.save(offering);
    }

    /** A member of staff with a TEACHER login, returning the employee id. */
    public UUID createTeacher(String username, String password) {
        testData.institution();
        int index = employeeCounter.incrementAndGet();
        Employee employee = new Employee();
        employee.setEmployeeCode("EMP-SEC-" + index);
        employee.setFirstName("Teacher");
        employee.setLastName(String.valueOf(index));
        employee.setJoinDate(testData.YEAR_START);
        employee.setEmail(username + "@sunrise.edu.np");
        Employee saved = employees.save(employee);
        User user = testData.user(username, "Teacher " + index, username + "@sunrise.edu.np",
                Role.TEACHER, password);
        user.setEmployeeId(saved.getId());
        users.save(user);
        return saved.getId();
    }

    /** Puts an existing student into the class and section the offering teaches. */
    public void enroll(UUID studentId, UUID offeringId) {
        CourseOffering offering = offerings.findById(offeringId)
                .orElseThrow(() -> new IllegalStateException("Offering not found"));
        if (offering.getAcademicYear() == null || offering.getSchoolClass() == null) {
            throw new IllegalStateException("Offering has no class/year to enroll into");
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudentId(studentId);
        enrollment.setAcademicYearId(offering.getAcademicYear().getId());
        enrollment.setSchoolClassId(offering.getSchoolClass().getId());
        enrollment.setSectionId(offering.getSection() == null ? null : offering.getSection().getId());
        enrollment.setStatus(Enrollment.Status.ACTIVE);
        enrollments.save(enrollment);
    }

    public UUID createStudent(String email) {
        testData.institution();
        int index = studentCounter.incrementAndGet();
        Student student = studentCreation.create(new StudentDtos.StudentRequest(
                "Test", null, "Student" + index, null, null,
                "Nepal", "+977-980000" + String.format("%04d", index), email,
                null, null, Student.Status.ACTIVE.name()));
        students.save(student);
        return student.getId();
    }

    public Semester createSemester(AcademicYear year, String name, int ordinal) {
        Semester semester = new Semester();
        semester.setAcademicYear(year);
        semester.setName(name);
        semester.setOrdinal(ordinal);
        semester.setStartDate(year.getStartDate());
        semester.setEndDate(year.getEndDate());
        semester.setStatus(Semester.Status.ACTIVE);
        return semesters.save(semester);
    }
}