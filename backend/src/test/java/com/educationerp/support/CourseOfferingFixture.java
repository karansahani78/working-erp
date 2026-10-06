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
import com.educationerp.student.Student;
import com.educationerp.student.service.StudentCreationService;
import com.educationerp.student.StudentRepository;
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
    private final TestData testData;

    private final AtomicInteger studentCounter = new AtomicInteger();

    public CourseOfferingFixture(CourseRepository courses,
                                 SchoolClassRepository classes,
                                 SectionRepository sections,
                                 SemesterRepository semesters,
                                 CourseOfferingRepository offerings,
                                 StudentRepository students,
                                 StudentCreationService studentCreation,
                                 TestData testData) {
        this.courses = courses;
        this.classes = classes;
        this.sections = sections;
        this.semesters = semesters;
        this.offerings = offerings;
        this.students = students;
        this.studentCreation = studentCreation;
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