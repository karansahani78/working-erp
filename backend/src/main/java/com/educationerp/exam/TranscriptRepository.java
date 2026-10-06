package com.educationerp.exam;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<Transcript, UUID> {

    Optional<Transcript> findByReferenceCodeIgnoreCase(String referenceCode);

    List<Transcript> findByStudentIdOrderByCreatedAtDesc(UUID studentId);
}
