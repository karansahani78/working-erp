package com.educationerp.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface FeeComponentRepository extends JpaRepository<FeeComponent, UUID> {

    List<FeeComponent> findByFeeStructureIdOrderByComponentTypeAsc(UUID feeStructureId);
}
