package com.educationerp.academic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramVersionRepository extends JpaRepository<ProgramVersion, UUID> {

    List<ProgramVersion> findByProgramIdOrderByEffectiveFromDesc(UUID programId);

    Optional<ProgramVersion> findByProgramIdAndLabelIgnoreCase(UUID programId, String label);

    Optional<ProgramVersion> findFirstByProgramIdAndStatusOrderByEffectiveFromDesc(UUID programId, ProgramVersion.Status status);
}
