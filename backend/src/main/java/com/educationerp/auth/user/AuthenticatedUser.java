package com.educationerp.auth.user;

import java.util.UUID;

/**
 * Authenticated principal attached to the security context. Carries the resolved
 * permission set and portal flags so downstream object-level checks stay cheap.
 */
public record AuthenticatedUser(
        UUID userId,
        String username,
        String displayName,
        java.util.Set<String> roles,
        java.util.Set<String> permissions,
        UUID studentId,
        java.util.Set<UUID> studentIds,
        String tokenVersion
) {

    public boolean hasRole(String role) {
        return roles.contains(role);
    }

    public boolean hasAnyRole(java.util.Collection<String> candidates) {
        return roles.stream().anyMatch(candidates::contains);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }

    public boolean hasAnyPermission(java.util.Collection<String> candidates) {
        return permissions.stream().anyMatch(candidates::contains);
    }

    public boolean isStaff() {
        return roles.stream().anyMatch(r -> !r.equals("STUDENT") && !r.equals("PARENT"));
    }

    public boolean isAdmin() {
        return hasRole("SUPER_ADMIN") || hasRole("INSTITUTION_ADMIN");
    }

    public boolean isStudent() {
        return hasRole("STUDENT");
    }

    public boolean isParent() {
        return hasRole("PARENT");
    }

    public boolean isTeacher() {
        return hasRole("TEACHER");
    }
}
