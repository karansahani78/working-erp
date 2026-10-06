package com.educationerp.document;

import org.springframework.data.jpa.repository.JpaRepository;

import com.educationerp.auth.role.Role;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DocumentAccessRepository extends JpaRepository<DocumentAccess, UUID> {

    List<DocumentAccess> findByDocumentIdOrderByGrantedAtDesc(UUID documentId);

    Optional<DocumentAccess> findByDocumentIdAndPrincipalUserId(UUID documentId, UUID userId);

    Optional<DocumentAccess> findByDocumentIdAndPrincipalRole(UUID documentId, Role role);
}
