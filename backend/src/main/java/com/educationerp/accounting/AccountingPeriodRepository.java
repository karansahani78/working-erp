package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, UUID> {

    List<AccountingPeriod> findByFiscalYearIdOrderByStartDateAsc(UUID fiscalYearId);

    Optional<AccountingPeriod> findFirstByStartDateLessThanEqualAndEndDateGreaterThanEqualOrderByStartDateDesc(
            LocalDate start, LocalDate end);
}
