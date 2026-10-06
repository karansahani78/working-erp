package com.educationerp.auth.web;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.Set;
import java.util.UUID;

/**
 * Payloads for user and role administration. Passwords appear only on the way in and are
 * never echoed back in a response.
 */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record CreateUserRequest(
            @NotBlank @Size(max = 80)
            @Pattern(regexp = "[A-Za-z0-9._-]+", message = "may only contain letters, digits, dot, dash and underscore")
            String username,
            @NotBlank @Size(min = 10, max = 128) String password,
            @NotBlank @Size(max = 150) String displayName,
            @Email @Size(max = 180) String email,
            @Size(max = 40) String phone,
            @NotNull String role,
            UUID studentId,
            UUID employeeId,
            Boolean mustChangePassword) {
    }

    public record UpdateUserRequest(
            @NotBlank @Size(max = 150) String displayName,
            @Email @Size(max = 180) String email,
            @Size(max = 40) String phone,
            @NotNull String role,
            @NotNull String status,
            Boolean mustChangePassword) {
    }

    public record ResetPasswordRequest(@NotBlank @Size(min = 10, max = 128) String password) {
    }

    public record UserResponse(
            UUID id,
            String username,
            String displayName,
            String email,
            String phone,
            String role,
            String status,
            UUID studentId,
            UUID employeeId,
            boolean mustChangePassword,
            boolean emailVerified,
            java.time.Instant lastLoginAt,
            java.time.Instant createdAt) {
    }

    public record RoleSummary(
            UUID id,
            String code,
            String name,
            String description,
            boolean builtIn,
            long userCount,
            int permissionCount) {
    }

    public record RoleDetail(
            UUID id,
            String code,
            String name,
            String description,
            boolean builtIn,
            long userCount,
            Set<String> permissions) {
    }

    public record SaveRoleRequest(
            @NotBlank @Size(max = 100) String name,
            @Size(max = 255) String description,
            @NotNull Set<@NotBlank String> permissions) {
    }

    public record ModuleState(@NotNull String key, boolean enabled, boolean enabledByDefault) {
    }

    public record SetModuleRequest(@NotNull String key, @NotNull Boolean enabled) {
    }
}