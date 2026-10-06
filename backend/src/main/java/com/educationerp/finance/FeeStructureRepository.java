package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FeeStructureRepository extends JpaRepository<FeeStructure, UUID> {

    Optional<FeeStructure> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<FeeStructure> findByStatusOrderByNameAsc(FeeStructure.Status status);

    /**
     * The structure a newly admitted student should be assessed against: one written for
     * their own class if it exists, otherwise the year-wide structure. A class-specific
     * structure always wins, so a school's discount for one grade is not applied to the
     * rest of the school.
     */
    @Query("""
            select f from FeeStructure f
            where f.status = :status
              and f.academicYearId = :academicYearId
              and (f.schoolClassId = :schoolClassId or f.schoolClassId is null)
            order by case when f.schoolClassId = :schoolClassId then 0 else 1 end, f.name
            """)
    List<FeeStructure> findAssessableFor(@Param("status") FeeStructure.Status status,
                                         @Param("academicYearId") UUID academicYearId,
                                         @Param("schoolClassId") UUID schoolClassId);
}
