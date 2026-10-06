package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {

    Optional<Room> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    List<Room> findByActiveTrueOrderByNameAsc();

    List<Room> findByCampusIdAndActiveTrueOrderByNameAsc(UUID campusId);
}
