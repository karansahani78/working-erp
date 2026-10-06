package com.educationerp.library;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public interface LibraryFineRepository extends JpaRepository<LibraryFine, UUID> {

    List<LibraryFine> findByMemberIdOrderByAssessedOnDesc(UUID memberId);

    List<LibraryFine> findByMemberIdAndStatusOrderByAssessedOnDesc(UUID memberId,
                                                                  LibraryFine.Status status);

    Page<LibraryFine> findByStatusOrderByAssessedOnAsc(LibraryFine.Status status, Pageable pageable);

    /** What is owed in total, summed in the database rather than by loading every fine. */
    @Query("select coalesce(sum(f.amount), 0) from LibraryFine f where f.status = :status")
    BigDecimal sumByStatus(@Param("status") LibraryFine.Status status);
}
