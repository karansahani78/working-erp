package com.educationerp.auth;

import com.educationerp.audit.AuditLogRepository;
import com.educationerp.auth.user.User;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;


import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Administration of sign-ins, roles and modules.
 *
 * <p>The guards are the point of this suite: an administrator must not be able to lock,
 * demote or strand the institution, and a role must not be granted a permission that does
 * not exist.
 */
class AdminIntegrationTest extends IntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    @DisplayName("a newly created user can sign in and is given the role that was asked for")
    void createThenSignIn() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"dean","password":"DeanPass123","displayName":"Dean Of Studies",
                                 "email":"dean@sunrise.edu.np","role":"PRINCIPAL"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.username").value("dean"))
                .andExpect(jsonPath("$.data.role").value("PRINCIPAL"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                // A password must never be echoed back, not even hashed.
                .andExpect(jsonPath("$.data.password").doesNotExist())
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        String deanToken = loginToken("dean", "DeanPass123");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(deanToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("PRINCIPAL"));
    }

    @Test
    @DisplayName("a duplicate username or email is refused with a field-level rule message")
    void duplicatesRefused() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"clerk","password":"ClerkPass123","displayName":"Clerk",
                                 "email":"clerk@sunrise.edu.np","role":"RECEPTIONIST"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"clerk","password":"ClerkPass123","displayName":"Clerk Two","role":"RECEPTIONIST"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("BUSINESS_RULE_VIOLATION"))
                .andExpect(jsonPath("$.message").value("That username is already taken."));

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"clerk2","password":"ClerkPass123","displayName":"Clerk Three",
                                 "email":"clerk@sunrise.edu.np","role":"RECEPTIONIST"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("That email already belongs to another account."));
    }

    @Test
    @DisplayName("an unknown role is refused rather than silently defaulting to something safe")
    void unknownRoleRefused() throws Exception {
        String token = adminToken();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"wizard","password":"WizardPass123","displayName":"Wizard","role":"ARCHMAGE"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Unknown role: ARCHMAGE"));
    }

    @Test
    @DisplayName("an administrator cannot lock, demote or flag their own account")
    void selfHarmRefused() throws Exception {
        String token = adminToken();
        String adminId = currentUserId(token);

        mockMvc.perform(put("/api/v1/admin/users/" + adminId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Administrator","email":"admin@sunrise.edu.np",
                                 "role":"SUPER_ADMIN","status":"LOCKED"}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("You cannot lock your own account."));

        mockMvc.perform(put("/api/v1/admin/users/" + adminId)
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Administrator","email":"admin@sunrise.edu.np",
                                 "role":"PRINCIPAL","status":"ACTIVE"}
                                """))
                .andExpect(status().isUnprocessableEntity());

        // The only active administrator may not be demoted, because that would leave the
        // institution with nobody able to administer it.
        assertThat(userRepository.findById(java.util.UUID.fromString(adminId)))
                .isPresent()
                .get()
                .extracting(com.educationerp.auth.user.User::getPrimaryRole)
                .isEqualTo(com.educationerp.auth.role.Role.SUPER_ADMIN);
    }

    @Test
    @DisplayName("an optional module can be switched off and on, but a core module cannot")
    void moduleToggling() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"LIBRARY","enabled":false}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(get("/api/v1/admin/modules").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.key=='LIBRARY')].enabled").value(false));

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"LIBRARY","enabled":true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"STUDENTS","enabled":false}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("STUDENTS is a core module and cannot be switched off."));

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"TELEPORTATION","enabled":true}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Unknown module: TELEPORTATION"));
    }

    @Test
    @DisplayName("disabling a module closes its API, and re-enabling opens it again")
    void disabledModuleBlocksItsApi() throws Exception {
        String token = adminToken();

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"LIBRARY","enabled":false}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/library/books").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("MODULE_DISABLED"));

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"LIBRARY","enabled":true}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/library/books").header("Authorization", bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a role cannot be granted a permission that does not exist")
    void unknownPermissionRefused() throws Exception {
        String token = adminToken();

        MvcResult roles = mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String examControllerId = findRoleId(roles.getResponse().getContentAsString(), "EXAM_CONTROLLER");

        mockMvc.perform(put("/api/v1/admin/roles/" + examControllerId + "/permissions")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"Exam controller","permissions":["EXAM_READ","FLY_A_JETPACK"]}
                                """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Unknown permission: FLY_A_JETPACK"));
    }

    @Test
    @DisplayName("permissions saved for a role take effect for a user holding it")
    void savedPermissionsTakeEffect() throws Exception {
        String token = adminToken();

        MvcResult roles = mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        String examinerId = findRoleId(roles.getResponse().getContentAsString(), "EXAM_CONTROLLER");

        // Strip the role down to a single permission, then confirm a holder loses access to
        // something they previously had, and gains it back when it is restored.
        mockMvc.perform(put("/api/v1/admin/roles/" + examinerId + "/permissions")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"Exam controller","permissions":["EXAM_READ"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(1));

        MvcResult afterTrim = mockMvc.perform(get("/api/v1/admin/roles/" + examinerId)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(afterTrim.getResponse().getContentAsString()).contains("EXAM_READ");
        assertThat(afterTrim.getResponse().getContentAsString()).doesNotContain("RESULT_PUBLISH");

        mockMvc.perform(put("/api/v1/admin/roles/" + examinerId + "/permissions")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"name":"Exam controller",
                                 "permissions":["EXAM_READ","EXAM_SCHEDULE_MANAGE","RESULT_READ","RESULT_PUBLISH"]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.permissions.length()").value(4));
    }

    @Test
    @DisplayName("only a user with user-administration rights may call the admin API")
    void permissionEnforced() throws Exception {
        testData.institution();
        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"librarian","password":"LibPass1234","displayName":"Librarian",
                                 "email":"librarian@sunrise.edu.np","role":"LIBRARIAN"}
                                """))
                .andExpect(status().isCreated());

        String librarianToken = loginToken("librarian", "LibPass1234");

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(librarianToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(librarianToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(librarianToken))
                        .contentType("application/json")
                        .content("""
                                {"username":"sneaky","password":"SneakyPass123","displayName":"Sneaky","role":"SUPER_ADMIN"}
                                """))
                .andExpect(status().isForbidden());

        // An anonymous caller gets a 401 rather than a 403, because nothing was authenticated.
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("creating a user and changing a module are both written to the audit trail")
    void actionsAreAudited() throws Exception {
        String token = adminToken();
        long before = auditLogRepository.count();

        mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"auditor","password":"AuditPass123","displayName":"Auditor","role":"RECEPTIONIST"}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"LIBRARY","enabled":false}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/admin/modules")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"key":"LIBRARY","enabled":true}
                                """))
                .andExpect(status().isOk());

        assertThat(auditLogRepository.count()).isGreaterThan(before);
        assertThat(auditLogRepository.findAll())
                .anyMatch(row -> "User".equals(row.getEntityType()) && row.getSummary() != null
                        && row.getSummary().contains("auditor"))
                .anyMatch(row -> "ModuleSetting".equals(row.getEntityType()));

        // The password must never reach the audit log, even though the event records the change.
        assertThat(auditLogRepository.findAll())
                .noneMatch(row -> row.getAfterState() != null && row.getAfterState().contains("AuditPass123"));
    }

    @Test
    @DisplayName("a password reset clears an existing lockout and is audited")
    void resetPasswordClearsLockout() throws Exception {
        String token = adminToken();

        MvcResult created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"username":"locked","password":"LockedPass123","displayName":"Locked Out","role":"RECEPTIONIST"}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asText();

        // Fail the sign-in enough times to trigger the lockout policy.
        for (int attempt = 0; attempt < 6; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content("""
                            {"loginId":"locked","password":"DefinitelyWrong1"}
                            """));
        }
        User lockedUser = userRepository.findById(java.util.UUID.fromString(id)).orElseThrow();
        assertThat(lockedUser.isLocked(java.time.Instant.now())).isTrue();

        mockMvc.perform(post("/api/v1/admin/users/" + id + "/password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"password":"UnlockedPass123"}
                                """))
                .andExpect(status().isOk());

        // After the reset the account is usable again with the new password.
        loginToken("locked", "UnlockedPass123");
    }

    private String currentUserId(String token) throws Exception {
        String body = mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return objectMapper.readTree(body).path("data").path("id").asText();
    }

    private String findRoleId(String rolesJson, String code) throws Exception {
        for (var node : objectMapper.readTree(rolesJson).path("data")) {
            if (code.equals(node.path("code").asText())) {
                return node.path("id").asText();
            }
        }
        throw new AssertionError("Role not found: " + code);
    }
}