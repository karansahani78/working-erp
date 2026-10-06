package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {

    Optional<SchoolClass> findByAcademicYearIdAndCodeIgnoreCase(UUID academicYearId, String code);

    List<SchoolClass> findByAcademicYearIdAndActiveTrueOrderByOrdinalAscNameAsc(UUID academicYearId);

    boolean existsByAcademicYearIdAndCodeIgnoreCase(UUID academicYearId, String code);

    @Query("""
            select c from SchoolClass c join fetch c.academicYear y
            where (:yearId is null or y.id = :yearId) and (:term is null or lower(c.name) like :term or lower(c.code) like :term)
            """)
    Page<SchoolClass> search(@Param("yearId") UUID yearId, @Param("term") String term, Pageable pageable);
}
