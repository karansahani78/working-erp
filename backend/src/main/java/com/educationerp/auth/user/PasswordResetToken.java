package com.educationerp.auth.user;

import com.educationerp.common.persistence.ImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Single-use, expiring password reset / verification token. Only a digest is stored.
 */
@Entity
@Table(name = "password_reset_tokens", indexes = {
        @Index(name = "idx_pw_reset_user", columnList = "user_id"),
        @Index(name = "idx_pw_reset_hash", columnList = "token_hash", unique = true)
})
public class PasswordResetToken extends ImmutableEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "purpose", nullable = false, length = 30)
    private String purpose;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "requested_ip", length = 64)
    private String requestedIp;

    public static PasswordResetToken issue(UUID userId, String tokenHash, String purpose,
                                           Instant expiresAt, String requestedIp) {
        PasswordResetToken token = new PasswordResetToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.purpose = purpose;
        token.expiresAt = expiresAt;
        token.requestedIp = requestedIp;
        return token;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        this.usedAt = now;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public String getPurpose() {
        return purpose;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public String getRequestedIp() {
        return requestedIp;
    }
}
