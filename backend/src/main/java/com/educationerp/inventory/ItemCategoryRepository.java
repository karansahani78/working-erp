package com.educationerp.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ItemCategoryRepository extends JpaRepository<ItemCategory, UUID> {

    Optional<ItemCategory> findByCodeIgnoreCase(String code);

    Optional<ItemCategory> findByNameIgnoreCase(String name);
}
