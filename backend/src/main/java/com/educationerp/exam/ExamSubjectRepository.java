package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamSubjectRepository extends JpaRepository<ExamSubject, UUID> {

    List<ExamSubject> findByExaminationIdOrderBySubjectNameAsc(UUID examinationId);

    /** The subjects of a set of course offerings, for a teacher's own marking list. */
    List<ExamSubject> findByCourseOfferingIdIn(java.util.Collection<UUID> courseOfferingIds);

    Optional<ExamSubject> findByExaminationIdAndSubjectNameIgnoreCase(UUID examinationId, String subjectName);

    boolean existsByExaminationIdAndSubjectNameIgnoreCase(UUID examinationId, String subjectName);
}
