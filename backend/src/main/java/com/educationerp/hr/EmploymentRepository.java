package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmploymentRepository extends JpaRepository<Employment, UUID> {

    List<Employment> findByEmployeeIdOrderByStartDateDesc(UUID employeeId);

    /** The spell covering a date, used when reconstructing a payslip's job and pay. */
    List<Employment> findByEmployeeIdAndStartDateLessThanEqualOrderByStartDateDesc(UUID employeeId,
                                                                                  java.time.LocalDate on);
}