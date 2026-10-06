package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface BankReconciliationRepository extends JpaRepository<BankReconciliation, UUID> {

    List<BankReconciliation> findByBankAccountIdOrderByStatementDateDesc(UUID bankAccountId);
}
