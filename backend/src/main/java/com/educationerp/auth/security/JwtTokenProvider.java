package com.educationerp.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

/**
 * Issues and verifies HMAC-SHA256 access tokens and generates opaque refresh tokens.
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final String issuer;
    private final Duration accessTtl;

    public JwtTokenProvider(com.educationerp.auth.config.SecurityProperties properties) {
        String secret = properties.getJwt().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "erp.security.jwt.secret must be configured. Set the JWT_SECRET environment variable.");
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(secret);
            if (bytes.length < 32) {
                bytes = secret.getBytes(StandardCharsets.UTF_8);
            }
        } catch (IllegalArgumentException notBase64) {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        if (bytes.length < 32) {
            throw new IllegalStateException(
                    "erp.security.jwt.secret must be at least 32 bytes for HS256. Provide a longer JWT_SECRET.");
        }
        this.key = Keys.hmacShaKeyFor(bytes);
        this.issuer = properties.getJwt().getIssuer();
        this.accessTtl = properties.getJwt().getAccessTokenTtl();
    }

    public Duration accessTokenTtl() {
        return accessTtl;
    }

    public record AccessToken(String token, Instant expiresAt) {
    }

    public AccessToken createAccessToken(UUID userId, String username, java.util.Set<String> roles,
                                        java.util.Set<String> permissions, String tokenVersion,
                                        UUID studentId, java.util.Set<UUID> studentIds) {
        Instant now = Instant.now();
        Instant exp = now.plus(accessTtl);
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("sub", userId.toString());
        claims.put("username", username);
        claims.put("roles", roles);
        claims.put("perms", permissions.stream().sorted().toList());
        claims.put("tv", tokenVersion);
        if (studentId != null) {
            claims.put("sid", studentId.toString());
        }
        if (studentIds != null && !studentIds.isEmpty()) {
            claims.put("sids", studentIds.stream().map(UUID::toString).toList());
        }
        String token = Jwts.builder()
                .issuer(issuer)
                .subject(userId.toString())
                .claims(claims)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp))
                .signWith(key)
                .compact();
        return new AccessToken(token, exp);
    }

    /**
     * Compares the token version baked into the access token with the version resolved
     * from the database. A mismatch means the password changed after the token was
     * issued, so the token is no longer honoured.
     */
    public boolean isCurrentTokenVersion(Claims claims, String currentVersion) {
        Object claim = claims.get("tv");
        String presented = claim == null ? "0" : String.valueOf(claim);
        return presented.equals(currentVersion == null ? "0" : currentVersion);
    }

    public Claims parse(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(key)
                .requireIssuer(issuer)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String newRefreshTokenValue() {
        byte[] bytes = new byte[48];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
