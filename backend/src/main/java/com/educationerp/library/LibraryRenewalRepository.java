package com.educationerp.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LibraryRenewalRepository extends JpaRepository<LibraryRenewal, UUID> {

    List<LibraryRenewal> findByIssueIdOrderByRenewedAtAsc(UUID issueId);
}
