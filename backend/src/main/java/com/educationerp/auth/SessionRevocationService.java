package com.educationerp.auth;

import com.educationerp.auth.user.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Revokes every refresh token for a user in a transaction that commits regardless of
 * what the caller does afterwards.
 *
 * <p>This is deliberately separate from {@code AuthService}. Reuse detection and forced
 * sign-out both revoke sessions and then raise an exception; if the revocation shared
 * that transaction it would be rolled back, leaving compromised sessions usable.
 */
@Service
@RequiredArgsConstructor
public class SessionRevocationService {

    private final RefreshTokenRepository refreshTokenRepository;

    /** Revokes all sessions for the user. Returns how many were revoked. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeAllForUser(UUID userId) {
        return refreshTokenRepository.revokeAllForUser(userId);
    }

    /** Revokes the caller's other sessions while keeping the current one usable. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int revokeOthersForUser(UUID userId, String keepTokenHash) {
        return refreshTokenRepository.revokeAllForUserExcept(userId, keepTokenHash);
    }
}