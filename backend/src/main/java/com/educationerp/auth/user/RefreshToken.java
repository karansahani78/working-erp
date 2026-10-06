package com.educationerp.auth.user;

import com.educationerp.common.persistence.ImmutableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Refresh token record with rotation and reuse detection. Only a SHA-256 digest of the
 * token is stored, so a database leak cannot be replayed against the API.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_user", columnList = "user_id"),
        @Index(name = "idx_refresh_tokens_hash", columnList = "token_hash", unique = true),
        @Index(name = "idx_refresh_tokens_expires", columnList = "expires_at")
})
public class RefreshToken extends ImmutableEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private RefreshTokenStatus status = RefreshTokenStatus.ACTIVE;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "rotated_to_id")
    private UUID rotatedToId;

    @Column(name = "client_ip", length = 64)
    private String clientIp;

    @Column(name = "user_agent", length = 400)
    private String userAgent;

    public static RefreshToken issue(UUID userId, String tokenHash, Instant issuedAt, Instant expiresAt,
                                     String clientIp, String userAgent) {
        RefreshToken token = new RefreshToken();
        token.userId = userId;
        token.tokenHash = tokenHash;
        token.status = RefreshTokenStatus.ACTIVE;
        token.issuedAt = issuedAt;
        token.expiresAt = expiresAt;
        token.clientIp = clientIp;
        token.userAgent = userAgent;
        return token;
    }

    public boolean isUsable(Instant now) {
        return status == RefreshTokenStatus.ACTIVE && expiresAt.isAfter(now);
    }

    public void markRotated(UUID successorId) {
        this.status = RefreshTokenStatus.ROTATED;
        this.rotatedToId = successorId;
    }

    public void markRevoked() {
        this.status = RefreshTokenStatus.REVOKED;
    }

    public void markCompromised() {
        this.status = RefreshTokenStatus.COMPROMISED;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public RefreshTokenStatus getStatus() {
        return status;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public UUID getRotatedToId() {
        return rotatedToId;
    }

    public String getClientIp() {
        return clientIp;
    }

    public String getUserAgent() {
        return userAgent;
    }
}
