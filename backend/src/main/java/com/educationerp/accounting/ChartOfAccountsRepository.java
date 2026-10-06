package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ChartOfAccountsRepository extends JpaRepository<ChartOfAccounts, UUID> {

    Optional<ChartOfAccounts> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<ChartOfAccounts> findByActiveTrueOrderByCodeAsc();

    List<ChartOfAccounts> findByPostableTrueAndActiveTrueOrderByCodeAsc();
}
