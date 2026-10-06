package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SalaryStructureRepository extends JpaRepository<SalaryStructure, UUID> {

    Optional<SalaryStructure> findByCodeIgnoreCase(String code);

    List<SalaryStructure> findByStatusOrderByNameAsc(SalaryStructure.Status status);

    /**
     * The structure with its components attached, so payroll can compute a payslip without
     * an extra round trip per employee.
     */
    @Query("""
            select distinct s from SalaryStructure s
            left join fetch s.components
            where s.id = :id
            """)
    Optional<SalaryStructure> findWithComponentsById(UUID id);
}