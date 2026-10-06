package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeSlotRepository extends JpaRepository<TimeSlot, UUID> {

    List<TimeSlot> findByActiveTrueOrderByOrdinalAsc();

    Optional<TimeSlot> findByNameIgnoreCase(String name);
}
