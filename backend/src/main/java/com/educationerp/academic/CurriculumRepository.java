package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CurriculumRepository extends JpaRepository<Curriculum, UUID> {

    List<Curriculum> findByProgramVersionIdAndActiveTrue(UUID programVersionId);

    Optional<Curriculum> findByProgramVersionIdAndNameIgnoreCase(UUID programVersionId, String name);

    Optional<Curriculum> findFirstByProgramVersionIdAndActiveTrueOrderByCreatedAtDesc(UUID programVersionId);
}
