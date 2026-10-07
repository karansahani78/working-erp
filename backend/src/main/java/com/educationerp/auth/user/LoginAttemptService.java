package com.educationerp.auth.user;

import com.educationerp.auth.security.AuthenticatedUserLoader;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Tracks failed sign-in attempts for account lockout.
 *
 * <p>This lives apart from {@code AuthService} deliberately. Login throws when a
 * credential check fails, and a runtime exception rolls back the surrounding
 * transaction — which would silently discard the failure counter and make lockout
 * unreachable. Recording the attempt in its own transaction means the counter commits
 * whether or not the login itself succeeds.
 */
@Service
@RequiredArgsConstructor
public class LoginAttemptService {

    private final UserRepository userRepository;
    private final AuthenticatedUserLoader userLoader;

    /**
     * Increments the failure counter and locks the account once the threshold is reached.
     *
     * @return true when this attempt caused the account to be locked
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean registerFailure(UUID userId, int maxAttempts, Duration lockDuration, String ip) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return false;
        }
        Instant now = Instant.now();
        user.registerFailedLogin(now, maxAttempts, lockDuration, ip);
        userRepository.save(user);
        // A failed attempt may have started a lockout, so the cached principal must not
        // keep treating the account as free while its access tokens still circulate.
        userLoader.invalidate(userId);
        return user.isLocked(now);
    }

    /** Clears the counter after a successful sign-in. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void registerSuccess(UUID userId) {
        userRepository.findById(userId).ifPresent(user -> {
            user.registerSuccessfulLogin(Instant.now());
            userRepository.save(user);
        });
    }

    /** Current failure count, used to report attempts remaining without guessing. */
    @Transactional(readOnly = true)
    public int failureCount(UUID userId) {
        return userRepository.findById(userId)
                .map(User::getFailedLoginCount)
                .orElse(0);
    }
}