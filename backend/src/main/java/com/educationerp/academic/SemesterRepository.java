package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    List<Semester> findByAcademicYearIdOrderByOrdinal(UUID academicYearId);

    Optional<Semester> findByAcademicYearIdAndOrdinal(UUID academicYearId, Integer ordinal);

    List<Semester> findByStatus(Semester.Status status);

    long countByAcademicYearId(UUID academicYearId);

    @Query("""
            select s from Semester s
            join fetch s.academicYear y
            where (:term is null or lower(s.name) like :term or lower(y.name) like :term)
            """)
    Page<Semester> search(@Param("term") String term, Pageable pageable);
}
