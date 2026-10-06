package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradeBoundaryRepository extends JpaRepository<GradeBoundary, UUID> {

    List<GradeBoundary> findByGradingScaleIdOrderByMinPercentageDesc(UUID gradingScaleId);

    Optional<GradeBoundary> findByGradingScaleIdAndLetterGradeIgnoreCase(UUID gradingScaleId, String letterGrade);
}
