package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ScholarshipRepository extends JpaRepository<Scholarship, UUID> {

    Optional<Scholarship> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Scholarship> findByStatusOrderByNameAsc(Scholarship.Status status);
}
