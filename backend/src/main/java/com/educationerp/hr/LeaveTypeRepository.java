package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    Optional<LeaveType> findByCodeIgnoreCase(String code);

    List<LeaveType> findByActiveTrueOrderByNameAsc();
}