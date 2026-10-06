package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurriculumCourseRepository extends JpaRepository<CurriculumCourse, UUID> {

    List<CurriculumCourse> findByCurriculumIdOrderBySemesterOrdinalAscOrdinalAsc(UUID curriculumId);

    List<CurriculumCourse> findByCurriculumIdAndSemesterId(UUID curriculumId, UUID semesterId);

    Optional<CurriculumCourse> findByCurriculumIdAndSemesterIdAndCourseId(UUID curriculumId, UUID semesterId, UUID courseId);

    @Query("select coalesce(sum(cc.creditHours), 0) from CurriculumCourse cc where cc.curriculum.id = :curriculumId")
    int totalCredits(@Param("curriculumId") UUID curriculumId);

    @Query("select cc from CurriculumCourse cc where cc.semester.id = :semesterId and cc.curriculum.id in :curriculumIds")
    List<CurriculumCourse> findBySemesterIdAndCurriculumIdIn(@Param("semesterId") UUID semesterId,
                                                            @Param("curriculumIds") List<UUID> curriculumIds);
}
