package com.educationerp.auth.service;

import com.educationerp.audit.AuditAction;
import com.educationerp.audit.AuditEvent;
import com.educationerp.audit.AuditService;
import com.educationerp.auth.permission.Permission;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.role.RoleDefaults;
import com.educationerp.auth.security.AuthorizationChecker;
import com.educationerp.auth.user.AuthenticatedUser;
import com.educationerp.auth.user.RoleDefinition;
import com.educationerp.auth.user.RoleDefinitionRepository;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.auth.user.UserStatus;
import com.educationerp.auth.web.AdminDtos;
import com.educationerp.common.error.AppException;
import com.educationerp.common.error.ErrorCode;
import com.educationerp.institution.InstitutionService;
import com.educationerp.institution.ModuleKey;
import com.educationerp.institution.ModuleSetting;
import com.educationerp.institution.ModuleSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * User, role and module administration.
 *
 * <p>Two rules run through everything here. A caller can never remove their own last means of
 * administering the institution, and no role may be granted a permission that does not exist.
 */
@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository users;
    private final RoleDefinitionRepository roles;
    private final ModuleSettingRepository moduleSettings;
    private final InstitutionService institutions;
    private final AuthorizationChecker authorization;
    private final PasswordEncoder passwordEncoder;
    private final AuditService audit;

    // ------------------------------------------------------------------ users

    @Transactional(readOnly = true)
    public Page<AdminDtos.UserResponse> searchUsers(String term, String role, String status, Pageable pageable) {
        authorization.requirePermission("USER_READ");
        Role parsedRole = parseRoleOrNull(role);
        UserStatus parsedStatus = parseStatusOrNull(status);
        String lowered = term == null || term.isBlank() ? null : "%" + term.trim().toLowerCase() + "%";
        return users.search(lowered, parsedRole, parsedStatus, pageable).map(this::toUserResponse);
    }

    @Transactional(readOnly = true)
    public AdminDtos.UserResponse getUser(UUID id) {
        authorization.requirePermission("USER_READ");
        return toUserResponse(users.findById(id).orElseThrow(() -> AppException.notFound("User")));
    }

    @Transactional
    public AdminDtos.UserResponse createUser(AdminDtos.CreateUserRequest request) {
        authorization.requirePermission("USER_CREATE");
        if (users.findByUsernameIgnoreCase(request.username().trim()).isPresent()) {
            throw AppException.rule("That username is already taken.");
        }
        if (request.email() != null && !request.email().isBlank()
                && users.findByEmailIgnoreCase(request.email().trim()).isPresent()) {
            throw AppException.rule("That email already belongs to another account.");
        }
        if (request.studentId() != null && users.findByStudentId(request.studentId()).isPresent()) {
            throw AppException.rule("That student already has a sign-in.");
        }
        if (request.employeeId() != null && users.findByEmployeeId(request.employeeId()).isPresent()) {
            throw AppException.rule("That employee already has a sign-in.");
        }

        Role role = parseRole(request.role());
        checkPasswordPolicy(request.password());

        User user = new User();
        user.setUsername(request.username().trim());
        user.setDisplayName(request.displayName().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setPrimaryRole(role);
        user.setPasswordChangedAt(Instant.now());
        user.setEmailVerified(false);
        user.setMustChangePassword(request.mustChangePassword() == null || request.mustChangePassword());
        if (request.email() != null && !request.email().isBlank()) {
            user.setEmail(request.email().trim().toLowerCase());
        }
        if (request.phone() != null && !request.phone().isBlank()) {
            user.setPhone(request.phone().trim());
        }
        if (request.studentId() != null) {
            user.setStudentId(request.studentId());
        }
        if (request.employeeId() != null) {
            user.setEmployeeId(request.employeeId());
        }

        users.save(user);

        audit.record(AuditEvent.builder()
                .action(AuditAction.CREATE)
                .entityType("User")
                .entityId(user.getId().toString())
                .entityLabel(user.getUsername())
                .summary("Created sign-in for " + user.getUsername() + " with role " + role.name())
                .after(Map.of("username", user.getUsername(), "role", role.name()))
                .succeeded(true)
                .build());

        return toUserResponse(user);
    }

    @Transactional
    public AdminDtos.UserResponse updateUser(UUID id, AdminDtos.UpdateUserRequest request) {
        authorization.requirePermission("USER_UPDATE");
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User"));
        Role nextRole = parseRole(request.role());
        UserStatus nextStatus = parseStatus(request.status());

        String beforeRole = user.getPrimaryRole().name();
        String beforeStatus = user.getStatus().name();

        if (user.getId().equals(currentUserId()) && nextStatus != UserStatus.ACTIVE) {
            throw AppException.rule("You cannot lock your own account.");
        }
        if (nextRole != Role.SUPER_ADMIN && user.getPrimaryRole() == Role.SUPER_ADMIN) {
            guardLastSuperAdmin(user, "demote");
        }
        if (user.getId().equals(currentUserId()) && nextRole != user.getPrimaryRole()) {
            throw AppException.rule("You cannot change your own role.");
        }
        if (user.getId().equals(currentUserId()) && Boolean.TRUE.equals(request.mustChangePassword())) {
            throw AppException.rule("You cannot require yourself to change your password.");
        }

        if (request.email() != null && !request.email().isBlank()) {
            String email = request.email().trim().toLowerCase();
            users.findByEmailIgnoreCase(email)
                    .filter(existing -> !existing.getId().equals(user.getId()))
                    .ifPresent(existing -> {
                        throw AppException.rule("That email already belongs to another account.");
                    });
            user.setEmail(email);
        }
        user.setDisplayName(request.displayName().trim());
        user.setPhone(request.phone() == null || request.phone().isBlank() ? null : request.phone().trim());
        user.setPrimaryRole(nextRole);
        user.setStatus(nextStatus);
        if (request.mustChangePassword() != null) {
            user.setMustChangePassword(request.mustChangePassword());
        }

        users.save(user);

        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("User")
                .entityId(user.getId().toString())
                .entityLabel(user.getUsername())
                .summary("Updated account " + user.getUsername())
                .before(Map.of("role", beforeRole, "status", beforeStatus))
                .after(Map.of("role", nextRole.name(), "status", nextStatus.name()))
                .succeeded(true)
                .build());

        return toUserResponse(user);
    }

    @Transactional
    public void resetPassword(UUID id, String password) {
        authorization.requirePermission("USER_UPDATE");
        User user = users.findById(id).orElseThrow(() -> AppException.notFound("User"));
        checkPasswordPolicy(password);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setPasswordChangedAt(Instant.now());
        user.clearLockout();
        users.save(user);

        audit.record(AuditEvent.builder()
                .action(AuditAction.PASSWORD_CHANGE)
                .entityType("User")
                .entityId(user.getId().toString())
                .entityLabel(user.getUsername())
                .summary("Reset the password for " + user.getUsername())
                .succeeded(true)
                .build());
    }

    // ------------------------------------------------------------------ roles

    @Transactional(readOnly = true)
    public List<AdminDtos.RoleSummary> listRoles() {
        authorization.requirePermission("ROLE_READ");
        Map<Role, Long> counts = roleCounts();
        List<RoleDefinition> all = roles.findAll();
        all.sort(Comparator.comparing(role -> role.getCode().name()));
        return all.stream().map(role -> new AdminDtos.RoleSummary(
                role.getId(),
                role.getCode().name(),
                role.getName(),
                role.getDescription(),
                role.isBuiltIn(),
                counts.getOrDefault(role.getCode(), 0L),
                role.getPermissions().size())).toList();
    }

    @Transactional(readOnly = true)
    public AdminDtos.RoleDetail getRole(UUID id) {
        authorization.requirePermission("ROLE_READ");
        RoleDefinition role = roles.findById(id).orElseThrow(() -> AppException.notFound("Role"));
        return new AdminDtos.RoleDetail(
                role.getId(),
                role.getCode().name(),
                role.getName(),
                role.getDescription(),
                role.isBuiltIn(),
                roleCounts().getOrDefault(role.getCode(), 0L),
                new LinkedHashSet<>(role.getPermissions()));
    }

    @Transactional
    public AdminDtos.RoleDetail savePermissions(UUID id, Set<String> permissions) {
        authorization.requirePermission("ROLE_UPDATE");
        RoleDefinition role = roles.findById(id).orElseThrow(() -> AppException.notFound("Role"));
        if (!role.isBuiltIn()) {
            throw AppException.rule("Only built-in roles exist in this release.");
        }

        Set<String> requested = permissions == null ? Set.of() : new LinkedHashSet<>(permissions);
        Set<String> unknown = new LinkedHashSet<>(requested);
        unknown.removeAll(knownPermissions());
        if (!unknown.isEmpty()) {
            throw AppException.rule("Unknown permission: " + unknown.iterator().next());
        }

        // A role must keep the ability to administer itself, or an institution can lock
        // every administrator out of the permission editor.
        if (role.getCode() == Role.SUPER_ADMIN) {
            requested.add("ROLE_UPDATE");
            requested.add("USER_UPDATE");
            requested.add("MODULE_CONFIG_UPDATE");
        }
        if (role.getCode() == Role.INSTITUTION_ADMIN) {
            requested.add("ROLE_UPDATE");
            requested.add("USER_UPDATE");
            requested.add("MODULE_CONFIG_UPDATE");
        }

        Set<String> before = new LinkedHashSet<>(role.getPermissions());
        role.getPermissions().clear();
        role.getPermissions().addAll(requested);
        roles.save(role);

        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("Role")
                .entityId(role.getId().toString())
                .entityLabel(role.getName())
                .summary("Updated permissions for " + role.getCode().name())
                .before(Map.of("permissions", before.size()))
                .after(Map.of("permissions", requested.size()))
                .module("ADMINISTRATION")
                .succeeded(true)
                .build());

        return getRole(role.getId());
    }

    // ------------------------------------------------------------------ modules

    @Transactional(readOnly = true)
    public List<AdminDtos.ModuleState> listModules() {
        authorization.requirePermission("MODULE_CONFIG_READ");
        Map<ModuleKey, Boolean> current = moduleSettings.asMap();
        List<AdminDtos.ModuleState> states = new ArrayList<>();
        for (ModuleKey key : ModuleKey.values()) {
            states.add(new AdminDtos.ModuleState(
                    key.name(),
                    current.getOrDefault(key, key.enabledByDefault()),
                    key.enabledByDefault()));
        }
        return states;
    }

    @Transactional
    public AdminDtos.ModuleState setModule(String key, boolean enabled) {
        authorization.requirePermission("MODULE_CONFIG_UPDATE");
        ModuleKey moduleKey;
        try {
            moduleKey = ModuleKey.valueOf(key);
        } catch (IllegalArgumentException ex) {
            throw AppException.rule("Unknown module: " + key);
        }
        if (moduleKey.enabledByDefault() && !enabled) {
            throw AppException.rule(moduleKey.name() + " is a core module and cannot be switched off.");
        }

        ModuleSetting setting = moduleSettings.findByModuleKey(moduleKey)
                .orElseGet(() -> ModuleSetting.of(moduleKey, enabled));
        boolean before = setting.isEnabled();
        setting.setEnabled(enabled);
        moduleSettings.save(setting);
        institutions.invalidateCache();

        audit.record(AuditEvent.builder()
                .action(AuditAction.UPDATE)
                .entityType("ModuleSetting")
                .entityId(setting.getId().toString())
                .entityLabel(moduleKey.name())
                .summary((enabled ? "Enabled" : "Disabled") + " module " + moduleKey.name())
                .before(Map.of("enabled", before))
                .after(Map.of("enabled", enabled))
                .succeeded(true)
                .build());

        return new AdminDtos.ModuleState(moduleKey.name(), enabled, moduleKey.enabledByDefault());
    }

    // ------------------------------------------------------------------ helpers

    private UUID currentUserId() {
        AuthenticatedUser current = authorization.currentUserOrNull();
        return current == null ? null : current.userId();
    }

    private void guardLastSuperAdmin(User user, String action) {
        boolean otherAdmins = users.findByPrimaryRoleAndStatus(Role.SUPER_ADMIN, UserStatus.ACTIVE).stream()
                .anyMatch(other -> !other.getId().equals(user.getId()));
        if (!otherAdmins) {
            throw AppException.rule("This is the only active administrator; " + action + " would leave nobody able to administer the institution.");
        }
    }

    private void checkPasswordPolicy(String password) {
        int minimum = authorization.passwordMinLength();
        if (password == null || password.length() < minimum) {
            throw AppException.rule("Use at least " + minimum + " characters.");
        }
        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw AppException.rule("Include at least one letter and one digit.");
        }
    }

    private Map<Role, Long> roleCounts() {
        Map<Role, Long> counts = new EnumMap<>(Role.class);
        for (User user : users.findAll()) {
            counts.merge(user.getPrimaryRole(), 1L, Long::sum);
        }
        return counts;
    }

    private Set<String> knownPermissions() {
        Set<String> names = new LinkedHashSet<>();
        for (Permission permission : Permission.values()) {
            names.add(permission.name());
        }
        return names;
    }

    private AdminDtos.UserResponse toUserResponse(User user) {
        return new AdminDtos.UserResponse(
                user.getId(),
                user.getUsername(),
                user.getDisplayName(),
                user.getEmail(),
                user.getPhone(),
                user.getPrimaryRole().name(),
                user.getStatus().name(),
                user.getStudentId(),
                user.getEmployeeId(),
                user.isMustChangePassword(),
                user.isEmailVerified(),
                user.getLastLoginAt(),
                user.getCreatedAt());
    }

    private Role parseRole(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AppException.rule("A role is required.");
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw AppException.rule("Unknown role: " + raw);
        }
    }

    private Role parseRoleOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Role.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw AppException.rule("Unknown role: " + raw);
        }
    }

    private UserStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            throw AppException.rule("A status is required.");
        }
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw AppException.rule("Unknown status: " + raw);
        }
    }

    private UserStatus parseStatusOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UserStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw AppException.rule("Unknown status: " + raw);
        }
    }

    /** Exposed so the permissions editor can group by the same defaults used at seed time. */
    public Set<String> builtinRolePermissions(Role role) {
        return RoleDefaults.forRole(role).stream().map(Enum::name).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }
}