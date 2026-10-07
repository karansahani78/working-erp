package com.educationerp.setup;

import com.educationerp.auth.permission.Permission;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.role.RoleDefaults;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reconciles built-in role definitions with the permissions shipped in this release.
 *
 * <p>An installation upgraded from an older build can carry role definitions that
 * predate a permission added to the default set. The setup wizard only fills an empty
 * definition, so the gap would otherwise persist forever and holders of a role would
 * quietly lose newer capabilities. This migration runs at startup and only ever adds
 * missing default permissions, nothing is removed, so the permission editor remains the
 * place to grant extras and schools are never silently locked out of a permission that
 * the product now ships as baseline.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RolePermissionMigrator {

    private final RoleDefinitionRepository roles;

    @Transactional
    @EventListener(ApplicationReadyEvent.class)
    public void migrateOnStartup() {
        reconcile();
    }

    @Transactional
    public void reconcile() {
        for (Role role : Role.values()) {
            RoleDefinition definition = roles.findByCode(role).orElse(null);
            if (definition == null || !definition.isBuiltIn()) {
                continue;
            }
            Set<String> shipped = RoleDefaults.forRole(role).stream()
                    .map(Permission::name)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Set<String> missing = new LinkedHashSet<>(shipped);
            missing.removeAll(definition.getPermissions());
            if (missing.isEmpty()) {
                continue;
            }
            if (log.isInfoEnabled()) {
                log.info("Role permission migration: added {} to {}", missing, role.name());
            }
            Set<String> merged = new LinkedHashSet<>(definition.getPermissions());
            merged.addAll(missing);
            definition.setPermissions(merged);
            roles.save(definition);
        }
    }
}