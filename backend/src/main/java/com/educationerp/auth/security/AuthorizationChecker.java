package com.educationerp.auth.security;

import com.educationerp.auth.config.SecurityProperties;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.UUID;

/**
 * Object-level and permission-level authorisation helpers used by application services.
 *
 * Frontend hiding is convenience only — every service method that touches a record calls
 * one of these guards, so authenticated-but-not-authorised is never sufficient.
 */
@Component
@RequiredArgsConstructor
public class AuthorizationChecker {

    private final SecurityProperties properties;

    public AuthenticatedUser currentUser() {
        return currentUserOrNull();
    }

    public AuthenticatedUser currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return user;
        }
        return null;
    }

    public AuthenticatedUser requireUser() {
        AuthenticatedUser user = currentUserOrNull();
        if (user == null) {
            throw new AppException(ErrorCode.AUTHENTICATION_REQUIRED);
        }
        return user;
    }

    public void requirePermission(String permission) {
        AuthenticatedUser user = requireUser();
        if (!user.hasPermission(permission)) {
            throw AppException.denied("You do not have permission to perform this action.");
        }
    }

    public void requireAnyPermission(Collection<String> permissions) {
        AuthenticatedUser user = requireUser();
        if (!user.hasAnyPermission(permissions)) {
            throw AppException.denied("You do not have permission to perform this action.");
        }
    }

    public void requireRole(String role) {
        AuthenticatedUser user = requireUser();
        if (!user.hasRole(role)) {
            throw AppException.denied("You do not have permission to perform this action.");
        }
    }

    public boolean hasPermission(String permission) {
        AuthenticatedUser user = currentUserOrNull();
        return user != null && user.hasPermission(permission);
    }

    /**
     * Verifies the caller may act on a specific student. Staff need
     * {@code STUDENT_READ}; students and parents are limited to their own scope.
     */
    public void requireStudentAccess(UUID studentId) {
        AuthenticatedUser user = requireUser();
        if (studentId == null) {
            throw AppException.notFound("Student");
        }
        if (user.hasPermission("STUDENT_READ")) {
            return;
        }
        if (canAccessStudent(user, studentId)) {
            return;
        }
        throw AppException.denied("You do not have permission to view this student's record.");
    }

    public boolean canAccessStudent(AuthenticatedUser user, UUID studentId) {
        if (user == null || studentId == null) {
            return false;
        }
        if (user.studentId() != null && user.studentId().equals(studentId)) {
            return true;
        }
        return user.studentIds() != null && user.studentIds().contains(studentId);
    }

    public int passwordMinLength() {
        return properties.getPassword().getMinLength();
    }
}
