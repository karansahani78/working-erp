package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CampusRepository extends JpaRepository<Campus, UUID> {

    List<Campus> findByActiveTrueOrderByNameAsc();
}
