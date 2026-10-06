package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FiscalYearRepository extends JpaRepository<FiscalYear, UUID> {

    Optional<FiscalYear> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    Optional<FiscalYear> findFirstByStatus(FiscalYear.Status status);

    List<FiscalYear> findByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            LocalDate start, LocalDate end);
}
