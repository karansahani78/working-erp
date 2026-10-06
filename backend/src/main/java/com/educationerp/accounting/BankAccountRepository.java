package com.educationerp.accounting;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    Optional<BankAccount> findByBankNameIgnoreCaseAndAccountNumber(String bankName, String accountNumber);

    boolean existsByBankNameIgnoreCaseAndAccountNumber(String bankName, String accountNumber);

    List<BankAccount> findByActiveTrueOrderByNameAsc();
}
