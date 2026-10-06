package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReportCardRepository extends JpaRepository<ReportCard, UUID> {

    Optional<ReportCard> findByStudentIdAndAcademicYearIdAndSemesterId(UUID studentId, UUID academicYearId, UUID semesterId);

    Optional<ReportCard> findByReferenceCodeIgnoreCase(String referenceCode);

    List<ReportCard> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
