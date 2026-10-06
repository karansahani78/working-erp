package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ResultRepository extends JpaRepository<Result, UUID> {

    Optional<Result> findByStudentIdAndExamSubjectId(UUID studentId, UUID examSubjectId);

    boolean existsByStudentIdAndExamSubjectId(UUID studentId, UUID examSubjectId);

    List<Result> findByExaminationIdAndStatus(Result examinationId, Result.ResultStatus status);

    List<Result> findByExaminationIdOrderByStudentIdAsc(UUID examinationId);

    List<Result> findByStudentIdAndStatusOrderByCreatedAtAsc(UUID studentId, Result.ResultStatus status);

    List<Result> findByStudentId(UUID studentId);
}
