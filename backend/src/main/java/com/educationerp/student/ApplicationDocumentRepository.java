package com.educationerp.student;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationDocumentRepository extends JpaRepository<ApplicationDocument, UUID> {

    List<ApplicationDocument> findByApplicationIdOrderByCreatedAtAsc(UUID applicationId);

    boolean existsByApplicationIdAndDocumentType(UUID applicationId, String documentType);
}