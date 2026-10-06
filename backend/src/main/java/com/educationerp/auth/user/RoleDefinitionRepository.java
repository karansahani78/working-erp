package com.educationerp.auth.user;

import com.educationerp.auth.role.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleDefinitionRepository extends JpaRepository<RoleDefinition, UUID> {

    Optional<RoleDefinition> findByCode(Role code);

    boolean existsByCode(Role code);

    Optional<RoleDefinition> findFirstByNameIgnoreCase(String name);
}
