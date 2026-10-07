package com.educationerp.setup;

import com.educationerp.auth.permission.Permission;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.role.RoleDefaults;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An upgraded installation can carry role definitions that predate a permission added
 * to the shipped defaults. The migration must close that gap by adding the missing
 * defaults, while never removing anything a school has configured.
 */
class RolePermissionMigrationTest extends IntegrationTest {

    @Autowired
    private RoleDefinitionRepository roles;

    @Autowired
    private RolePermissionMigrator migrator;

    @BeforeEach
    void seedInstitution() {
        testData.institution();
    }

    @Test
    @DisplayName("missing default permissions are added to an existing definition")
    void missingDefaultsAreRestored() {
        RoleDefinition teacher = roles.findByCode(Role.TEACHER).orElseThrow();
        Set<String> stale = new LinkedHashSet<>(teacher.getPermissions());
        stale.remove("ATTENDANCE_READ");
        stale.remove("REPORT_CARD_READ");
        teacher.setPermissions(stale);
        roles.save(teacher);

        migrator.reconcile();

        RoleDefinition migrated = roles.findByCode(Role.TEACHER).orElseThrow();
        assertThat(migrated.getPermissions())
                .contains("ATTENDANCE_READ", "REPORT_CARD_READ");
    }

    @Test
    @DisplayName("extras granted by the school are preserved and never removed")
    void customizedExtrasSurviveMigration() {
        RoleDefinition teacher = roles.findByCode(Role.TEACHER).orElseThrow();
        Set<String> customized = new LinkedHashSet<>(teacher.getPermissions());
        customized.add("INVOICE_READ");
        teacher.setPermissions(customized);
        roles.save(teacher);

        migrator.reconcile();

        RoleDefinition migrated = roles.findByCode(Role.TEACHER).orElseThrow();
        assertThat(migrated.getPermissions()).contains("INVOICE_READ");

        int expectedSize = RoleDefaults.forRole(Role.TEACHER).stream()
                .map(Permission::name)
                .collect(java.util.stream.Collectors.toSet()).size() + 1;
        assertThat(migrated.getPermissions().size()).isEqualTo(expectedSize);
    }

    @Test
    @DisplayName("reconciliation is idempotent")
    void reconciliationIsIdempotent() {
        migrator.reconcile();
        int sizeBefore = roles.findByCode(Role.TEACHER).orElseThrow().getPermissions().size();
        migrator.reconcile();
        assertThat(roles.findByCode(Role.TEACHER).orElseThrow().getPermissions().size()).isEqualTo(sizeBefore);
    }
}