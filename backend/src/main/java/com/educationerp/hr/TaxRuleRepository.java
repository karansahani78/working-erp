package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaxRuleRepository extends JpaRepository<TaxRule, UUID> {

    Optional<TaxRule> findByCodeIgnoreCase(String code);

    /**
     * The rule in force on a given date. Where a new version and an old one both claim the
     * date, the most recently effective one wins, so publishing a rate never leaves a gap.
     */
    @Query("""
            select r from TaxRule r
            where r.status = com.educationerp.hr.TaxRule$Status.ACTIVE
              and r.effectiveFrom <= :on
              and (r.effectiveTo is null or r.effectiveTo >= :on)
            order by r.effectiveFrom desc
            """)
    List<TaxRule> findEffectiveOn(LocalDate on);
}