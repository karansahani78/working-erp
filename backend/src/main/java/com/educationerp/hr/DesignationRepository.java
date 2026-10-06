package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DesignationRepository extends JpaRepository<Designation, UUID> {

    Optional<Designation> findByCodeIgnoreCase(String code);

    Optional<Designation> findByNameIgnoreCase(String name);

    List<Designation> findByActiveTrueOrderByNameAsc();
}