package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseOfferingRepository extends JpaRepository<CourseOffering, UUID> {

    @Query("""
            select o from CourseOffering o
            join fetch o.course c
            join fetch o.academicYear y
            left join fetch o.section s
            left join fetch o.schoolClass sc
            left join fetch o.semester sem
            where (:yearId is null or y.id = :yearId)
              and (:semesterId is null or sem.id = :semesterId)
              and (:classId is null or sc.id = :classId)
              and (:sectionId is null or s.id = :sectionId)
              and (:programId is null or o.program.id = :programId)
              and (:teacherId is null or o.teacherId = :teacherId)
              and (:term is null or lower(c.name) like :term or lower(c.code) like :term)
              and (:active is null or o.active = :active)
            """)
    Page<CourseOffering> search(@Param("yearId") UUID yearId,
                                @Param("semesterId") UUID semesterId,
                                @Param("classId") UUID classId,
                                @Param("sectionId") UUID sectionId,
                                @Param("programId") UUID programId,
                                @Param("teacherId") UUID teacherId,
                                @Param("term") String term,
                                @Param("active") Boolean active,
                                Pageable pageable);

    List<CourseOffering> findByTeacherIdAndAcademicYearIdAndActiveTrue(UUID teacherId, UUID academicYearId);

    List<CourseOffering> findByAcademicYearIdAndSemesterIdAndActiveTrue(UUID yearId, UUID semesterId);

    List<CourseOffering> findByAcademicYearIdAndSchoolClassIdAndSectionIdAndActiveTrue(UUID yearId, UUID classId, UUID sectionId);

    List<CourseOffering> findByAcademicYearIdAndProgramIdAndSemesterIdAndActiveTrue(UUID yearId, UUID programId, UUID semesterId);

    Optional<CourseOffering> findFirstByCourseIdAndAcademicYearIdAndSemesterIdAndSectionId(UUID courseId, UUID yearId,
                                                                                         UUID semesterId, UUID sectionId);

    long countBySemesterId(UUID semesterId);

    @Query("""
            select o from CourseOffering o
            join fetch o.course c
            join fetch o.academicYear y
            left join fetch o.section s
            left join fetch o.schoolClass sc
            left join fetch o.semester sem
            where y.id = :yearId and o.active = true
              and (:semesterId is null or sem.id = :semesterId)
              and (:classId is null or sc.id = :classId)
              and (:sectionId is null or s.id = :sectionId)
            """)
    List<CourseOffering> listForTeaching(@Param("yearId") UUID yearId,
                                         @Param("semesterId") UUID semesterId,
                                         @Param("classId") UUID classId,
                                         @Param("sectionId") UUID sectionId);

    @Query("""
            select o from CourseOffering o
            join fetch o.course c
            join fetch o.academicYear y
            left join fetch o.section s
            left join fetch o.schoolClass sc
            left join fetch o.semester sem
            left join fetch o.room r
            where o.teacherId = :teacherId and o.active = true
              and (:day is null or true)
            """)
    List<CourseOffering> listForTeacherTimetable(@Param("teacherId") UUID teacherId, @Param("day") DayOfWeek day);
}
