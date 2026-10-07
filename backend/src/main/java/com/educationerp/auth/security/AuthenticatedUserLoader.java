package com.educationerp.auth.security;

import com.educationerp.auth.permission.Permission;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.role.RoleDefaults;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds the security principal from the database, not from the token. The token only
 * proves who the caller claims to be; permissions are always re-resolved here.
 *
 * Results are cached briefly; any change to roles, permissions or account status
 * invalidates the entry.
 */
@Component
public class AuthenticatedUserLoader {

    private static final Logger log = LoggerFactory.getLogger(AuthenticatedUserLoader.class);

    private final UserRepository userRepository;
    private final RoleDefinitionRepository roleRepository;
    private final ObjectProvider<PortalScopeResolver> scopeResolver;
    private final Cache<UUID, AuthenticatedUser> cache;

    public AuthenticatedUserLoader(UserRepository userRepository,
                                   RoleDefinitionRepository roleRepository,
                                   ObjectProvider<PortalScopeResolver> scopeResolver) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.scopeResolver = scopeResolver;
        this.cache = Caffeine.newBuilder()
                .maximumSize(2_000)
                .expireAfterWrite(Duration.ofSeconds(60))
                .build();
    }

    public void invalidate(UUID userId) {
        if (userId != null) {
            cache.invalidate(userId);
        }
    }

    public void invalidateAll() {
        cache.invalidateAll();
    }

    @Transactional(readOnly = true)
    public Optional<AuthenticatedUser> load(UUID userId, boolean useCache) {
        if (useCache) {
            AuthenticatedUser cached = cache.getIfPresent(userId);
            if (cached != null) {
                return Optional.of(cached);
            }
        }
        Optional<User> found = userRepository.findById(userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        AuthenticatedUser principal = toPrincipal(found.get());
        if (useCache) {
            cache.put(userId, principal);
        }
        return Optional.of(principal);
    }

    @Transactional(readOnly = true)
    public AuthenticatedUser toPrincipal(User user) {
        Set<String> roles = new LinkedHashSet<>();
        roles.add(user.getPrimaryRole().name());

        // Effective permissions = union of every granted role (built-in + custom).
        Set<String> permissions = roleRepository.findByCode(user.getPrimaryRole())
                .map(RoleDefinition::getPermissions)
                .map(HashSetCopy::copy)
                .orElseGet(() -> RoleDefaults.forRole(user.getPrimaryRole()).stream()
                        .map(Permission::name)
                        .collect(Collectors.toCollection(LinkedHashSet::new)));

        Set<UUID> studentIds = scopeResolver.getIfAvailable() == null
                ? Set.of()
                : scopeResolver.getObject().studentIdsFor(user.getId());
        UUID primaryStudentId = user.getStudentId();
        if (primaryStudentId == null && !studentIds.isEmpty()) {
            primaryStudentId = studentIds.iterator().next();
        }
        log.debug("Resolved principal for user={} roles={} permissions={}", user.getUsername(), roles, permissions.size());
        return new AuthenticatedUser(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                Set.copyOf(roles),
                Set.copyOf(permissions),
                primaryStudentId,
                Set.copyOf(studentIds),
                tokenVersionOf(user),
                user.getStatus(),
                user.getLockedUntil());
    }

    /**
     * Access tokens are invalidated by rotating this value. It is derived from the last
     * password change, so changing a password (or an admin resetting one) immediately
     * stops already-issued access tokens from working, without waiting for expiry.
     */
    public static String tokenVersionOf(User user) {
        return user.getPasswordChangedAt() == null
                ? "0"
                : Long.toString(user.getPasswordChangedAt().toEpochMilli());
    }

    private static final class HashSetCopy {
        static Set<String> copy(Set<String> permissions) {
            return new LinkedHashSet<>(permissions);
        }
    }
}
