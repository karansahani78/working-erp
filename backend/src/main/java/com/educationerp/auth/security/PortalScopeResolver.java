package com.educationerp.auth.security;

import java.util.Set;
import java.util.UUID;

/**
 * Resolves which students a user may act on behalf of (their own record for a student,
 * their children for a parent). Implemented by the student module so the auth module
 * does not depend on it; absent implementation means "no linked students".
 */
public interface PortalScopeResolver {

    Set<UUID> studentIdsFor(UUID userId);

    default UUID primaryStudentIdFor(UUID userId) {
        return studentIdsFor(userId).stream().findFirst().orElse(null);
    }
}
