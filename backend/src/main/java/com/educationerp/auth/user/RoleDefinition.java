package com.educationerp.auth.user;

import com.educationerp.auth.role.Role;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/**
 * A role and its granted permissions. Built-in roles are seeded from
 * {@link com.educationerp.auth.role.RoleDefaults}; custom roles are created in Settings.
 */
@Entity
@Table(name = "roles")
public class RoleDefinition {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id = UUID.randomUUID();

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, unique = true, length = 40)
    private Role code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "built_in", nullable = false)
    private boolean builtIn = false;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "role_permissions", joinColumns = @JoinColumn(name = "role_id"))
    @Column(name = "permission", nullable = false, length = 60)
    private Set<String> permissions = new LinkedHashSet<>();

    protected RoleDefinition() {
    }

    public static RoleDefinition builtin(Role code, String name, String description, Set<String> permissions) {
        RoleDefinition role = new RoleDefinition();
        role.id = UUID.randomUUID();
        role.code = code;
        role.name = name;
        role.description = description;
        role.builtIn = true;
        role.permissions.addAll(permissions);
        return role;
    }

    public static RoleDefinition custom(String name, String description, Set<String> permissions) {
        RoleDefinition role = new RoleDefinition();
        role.id = UUID.randomUUID();
        role.code = null;
        role.name = name;
        role.description = description;
        role.builtIn = false;
        role.permissions.addAll(permissions);
        return role;
    }

    public UUID getId() {
        return id;
    }

    public Role getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public boolean isBuiltIn() {
        return builtIn;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(Set<String> permissions) {
        this.permissions = new LinkedHashSet<>(permissions);
    }
}
