package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface EmployeeQualificationRepository extends JpaRepository<EmployeeQualification, UUID> {

    List<EmployeeQualification> findByEmployeeIdOrderByIdAsc(UUID employeeId);

    boolean existsByEmployeeIdAndQualificationId(UUID employeeId, UUID qualificationId);
}