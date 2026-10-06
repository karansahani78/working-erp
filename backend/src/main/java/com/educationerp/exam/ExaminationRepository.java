package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExaminationRepository extends JpaRepository<Examination, UUID> {

    Optional<Examination> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Examination> findByStatusOrderByStartDateAsc(Examination.Status status);

    List<Examination> findByAcademicYearIdOrderByStartDateDesc(UUID academicYearId);
}
