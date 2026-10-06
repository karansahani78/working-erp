package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GradingScaleRepository extends JpaRepository<GradingScale, UUID> {

    Optional<GradingScale> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<GradingScale> findByActiveTrueOrderByNameAsc();
}
