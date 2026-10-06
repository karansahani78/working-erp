package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeLoanRepository extends JpaRepository<EmployeeLoan, UUID> {

    Optional<EmployeeLoan> findByReferenceIgnoreCase(String reference);

    List<EmployeeLoan> findByEmployeeIdAndStatusOrderByGrantedOnAsc(UUID employeeId, EmployeeLoan.Status status);

    List<EmployeeLoan> findByEmployeeIdOrderByGrantedOnDesc(UUID employeeId);
}