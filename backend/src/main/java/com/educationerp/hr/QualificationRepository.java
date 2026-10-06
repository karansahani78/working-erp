package com.educationerp.hr;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QualificationRepository extends JpaRepository<Qualification, UUID> {

    Optional<Qualification> findByNameIgnoreCase(String name);

    List<Qualification> findByActiveTrueOrderByNameAsc();
}