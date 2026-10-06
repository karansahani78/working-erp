package com.educationerp.institution;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface InstitutionRepository extends JpaRepository<Institution, UUID> {

    Optional<Institution> findFirstByOrderByCreatedAtAsc();

    Optional<Institution> findByInstitutionCodeIgnoreCase(String code);

    boolean existsByInstitutionCodeIgnoreCase(String code);
}
