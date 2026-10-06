package com.educationerp.auth;

import com.educationerp.auth.role.Role;
import com.educationerp.auth.user.UserStatus;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Authentication payloads. Every request record carries bean-validation annotations so
 * malformed input is rejected at the edge; business rules (lockout, password policy,
 * token reuse) live in {@link AuthService}.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @NotBlank @Size(max = 180) String loginId,
            @NotBlank @Size(max = 128) String password
    ) {
    }

    public record RefreshRequest(@NotBlank @Size(max = 400) String refreshToken) {
    }

    public record TokenResponse(
            String tokenType,
            String accessToken,
            Instant accessTokenExpiresAt,
            String refreshToken,
            Instant refreshTokenExpiresAt,
            UserProfile user
    ) {
    }

    /**
     * The signed-in user as the frontend needs it: identity, resolved permissions and the
     * portal flags that decide which navigation to render. Never contains credential
     * material or password hashes.
     */
    public record UserProfile(
            UUID id,
            String username,
            String displayName,
            String email,
            String phone,
            Role role,
            UserStatus status,
            Set<String> permissions,
            boolean staff,
            boolean student,
            boolean parent,
            UUID studentId,
            boolean mustChangePassword,
            Instant lastLoginAt
    ) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(max = 128) String newPassword
    ) {
    }

    public record ForgotPasswordRequest(
            @NotBlank @Size(max = 180) String identifier,
            @Email @Size(max = 180) String email
    ) {
    }

    public record ResetPasswordRequest(
            @NotBlank @Size(max = 200) String token,
            @NotBlank @Size(max = 128) String newPassword
    ) {
    }

    public record LogoutRequest(String refreshToken) {
    }

    public record MessageResponse(String message) {
    }

    /** Active session entry so a user can see and revoke other devices. */
    public record SessionResponse(
            UUID id,
            Instant issuedAt,
            Instant expiresAt,
            String status,
            String clientIp,
            String userAgent,
            boolean current
    ) {
    }
}