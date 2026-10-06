package com.educationerp.library;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LibraryCategoryRepository extends JpaRepository<LibraryCategory, UUID> {

    Optional<LibraryCategory> findByCodeIgnoreCase(String code);
    Optional<LibraryCategory> findByNameIgnoreCase(String name);
}
