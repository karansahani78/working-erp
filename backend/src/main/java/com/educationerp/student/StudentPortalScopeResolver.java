package com.educationerp.student;

import com.educationerp.auth.security.PortalScopeResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Works out whose records a signed-in person may see.
 *
 * <p>A student's own account resolves to themselves and a parent's to the children they are
 * actually linked to, so one person's login can never widen another person's reach. Everyone
 * else resolves to nothing, which is what keeps the portals closed by default: a signed-in
 * person is only ever in scope for what this returns, and staff work through permissions
 * instead.
 *
 * <p>This lives in the student module rather than in auth because that is where the knowledge
 * of who is related to whom lives.
 */
@Component
@RequiredArgsConstructor
public class StudentPortalScopeResolver implements PortalScopeResolver {

    private final UserLookup users;
    private final GuardianRepository guardians;

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> studentIdsFor(UUID userId) {
        if (userId == null) {
            return Set.of();
        }
        Set<UUID> scope = new LinkedHashSet<>();
        users.studentIdOf(userId).ifPresent(scope::add);
        List<UUID> children = guardians.studentIdsForUser(userId);
        scope.addAll(children);
        return scope;
    }
}
