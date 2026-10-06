package com.educationerp.academic;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AcademicYearRepository extends JpaRepository<AcademicYear, UUID> {

    Optional<AcademicYear> findByCodeIgnoreCase(String code);

    Optional<AcademicYear> findFirstByCurrentTrue();

    Optional<AcademicYear> findFirstByStatusOrderByStartDateDesc(AcademicYear.Status status);

    /** The academic year covering the supplied date, most recent first. */
    @Query("""
            select a from AcademicYear a
            where a.startDate <= :date and a.endDate >= :date
            order by a.startDate desc
            """)
    Optional<AcademicYear> findFirstByDateWithinOrderByStartDateDesc(@Param("date") LocalDate date);

    boolean existsByCodeIgnoreCase(String code);

    List<AcademicYear> findAllByOrderByStartDateDesc();

    @Query("select a from AcademicYear a where (:term is null or lower(a.name) like :term or lower(a.code) like :term)")
    Page<AcademicYear> search(@Param("term") String term, Pageable pageable);
}
