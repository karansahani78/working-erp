package com.educationerp.common.persistence;

import org.springframework.data.domain.AuditorAware;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Resolves the acting user for {@code created_by} / {@code updated_by}.
 * Returns {@link UUID#nameUUIDFromBytes} of the username for system-driven changes so
 * background jobs still leave a trace without requiring a real principal.
 */
@Component("auditorAware")
public class AuditorAwareImpl implements AuditorAware<UUID> {

    private static final UUID SYSTEM = UUID.nameUUIDFromBytes("system".getBytes(java.nio.charset.StandardCharsets.UTF_8));

    @Override
    public Optional<UUID> getCurrentAuditor() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Optional.of(SYSTEM);
        }
        if (auth.getPrincipal() instanceof com.educationerp.auth.user.AuthenticatedUser principal) {
            return Optional.of(principal.userId());
        }
        return Optional.of(SYSTEM);
    }

    public static UUID systemActor() {
        return SYSTEM;
    }
}
