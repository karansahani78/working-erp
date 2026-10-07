package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnrollmentRepository extends JpaRepository<Enrollment, UUID> {

    Optional<Enrollment> findFirstByStudentIdAndStatus(UUID studentId, Enrollment.Status status);

    Optional<Enrollment> findFirstByStudentIdAndAcademicYearIdAndStatus(UUID studentId,
                                                                      UUID academicYearId,
                                                                      Enrollment.Status status);

    boolean existsByStudentIdAndAcademicYearIdAndSemesterIdAndSchoolClassId(UUID studentId,
                                                                            UUID academicYearId,
                                                                            UUID semesterId,
                                                                            UUID schoolClassId);

    List<Enrollment> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

    /**
     * The enrolment of one student in one section. This is the single source of truth
     * for "is this student really in this class" used by attendance and marks scope.
     */
    Optional<Enrollment> findFirstByStudentIdAndAcademicYearIdAndSchoolClassIdAndSectionIdAndStatus(
            UUID studentId, UUID academicYearId, UUID schoolClassId, UUID sectionId, Enrollment.Status status);

    boolean existsByStudentIdAndAcademicYearIdAndSchoolClassIdAndSectionIdAndStatus(
            UUID studentId, UUID academicYearId, UUID schoolClassId, UUID sectionId, Enrollment.Status status);

    /**
     * The active students of one section, in roll order. This is how a teacher portal finds
     * the people in an assigned class: an offering names a class and a section, and the
     * enrollments do the rest.
     */
    List<Enrollment> findByAcademicYearIdAndSchoolClassIdAndStatusOrderByRollNumberAsc(
            UUID academicYearId, UUID schoolClassId, Enrollment.Status status);

    long countBySchoolClassIdAndStatus(UUID schoolClassId, Enrollment.Status status);

    long countBySectionIdAndStatus(UUID sectionId, Enrollment.Status status);
}