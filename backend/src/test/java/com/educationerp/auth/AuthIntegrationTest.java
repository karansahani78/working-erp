package com.educationerp.auth;

import com.educationerp.audit.AuditLogRepository;
import com.educationerp.auth.role.Role;
import com.educationerp.auth.user.UserRepository;
import com.educationerp.auth.user.UserStatus;
import com.educationerp.common.notification.NotificationService;
import com.educationerp.support.IntegrationTest;
import com.educationerp.support.TestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthIntegrationTest extends IntegrationTest {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("login with a correct password returns a token pair and the resolved profile")
    void loginSucceeds() throws Exception {
        testData.institution();
        testData.admin();

        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"admin","password":"%s"}
                                """.formatted(TestData.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("admin"))
                .andExpect(jsonPath("$.data.user.role").value("SUPER_ADMIN"))
                .andExpect(jsonPath("$.data.user.permissions").isArray())
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(TestData.ADMIN_PASSWORD);
    }

    @Test
    @DisplayName("login by email works as well as by login ID")
    void loginByEmail() throws Exception {
        testData.institution();
        testData.admin();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"admin@sunrise.edu.np","password":"%s"}
                                """.formatted(TestData.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value("admin"));
    }

    @Test
    @DisplayName("a wrong password is rejected without revealing that the account exists")
    void wrongPasswordRejected() throws Exception {
        testData.institution();
        testData.admin();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"admin","password":"WrongPass123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"nobody","password":"WrongPass123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andExpect(jsonPath("$.message").value("The login ID or password is incorrect."));
    }

    @Test
    @DisplayName("repeated failures lock the account and the lock is reported as ACCOUNT_LOCKED")
    void accountLocksAfterRepeatedFailures() throws Exception {
        testData.institution();
        testData.admin();

        for (int attempt = 0; attempt < 4; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType("application/json")
                            .content("""
                                    {"loginId":"admin","password":"WrongPass123"}
                                    """))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"admin","password":"WrongPass123"}
                                """))
                .andExpect(status().isLocked())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        // Even the correct password is refused while the lock stands.
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"admin","password":"%s"}
                                """.formatted(TestData.ADMIN_PASSWORD)))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("an inactive account cannot sign in")
    void inactiveAccountRejected() throws Exception {
        testData.institution();
        testData.admin();
        var user = testData.user("suspended", "Suspended User", "s@sunrise.edu.np",
                Role.TEACHER, "Teacher12345");
        user.setStatus(UserStatus.INACTIVE);
        userRepository.save(user);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"suspended","password":"Teacher12345"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    @DisplayName("refresh rotates the token; the old refresh token cannot be used again")
    void refreshRotatesAndDetectsReuse() throws Exception {
        testData.institution();
        testData.admin();

        Tokens tokens = loginTokens(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD);
        String firstRefresh = tokens.refreshToken();
        String accessToken = tokens.accessToken();

        String refreshResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String secondRefresh = objectMapper.readTree(refreshResponse).path("data").path("refreshToken").asText();
        assertThat(secondRefresh).isNotEqualTo(firstRefresh);

        // The rotated token must not work a second time.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", firstRefresh))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        // Reuse is treated as theft: the newly issued token is revoked as well.
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", secondRefresh))))
                .andExpect(status().isUnauthorized());

        assertThat(accessToken).isNotBlank();
    }

    @Test
    @DisplayName("an unknown refresh token is rejected")
    void unknownRefreshTokenRejected() throws Exception {
        testData.institution();
        testData.admin();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content("""
                                {"refreshToken":"not-a-real-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("protected endpoints require a token")
    void meRequiresToken() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
    }

    @Test
    @DisplayName("/me returns the signed-in profile with permissions")
    void meReturnsProfile() throws Exception {
        testData.institution();
        testData.admin();
        String token = adminToken();

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.permissions").isArray())
                .andExpect(jsonPath("$.data.emailVerified").doesNotExist());
    }

    @Test
    @DisplayName("logout revokes the session so its refresh token stops working")
    void logoutRevokesSession() throws Exception {
        testData.institution();
        testData.admin();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"loginId":"admin","password":"%s"}
                                """.formatted(TestData.ADMIN_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String body = login.getResponse().getContentAsString();
        String accessToken = objectMapper.readTree(body).path("data").path("accessToken").asText();
        String refreshToken = objectMapper.readTree(body).path("data").path("refreshToken").asText();

        mockMvc.perform(get("/api/v1/auth/sessions").header("Authorization", bearer(accessToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", bearer(accessToken))
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("changing the password invalidates the old access token and requires the new one")
    void changePasswordInvalidatesTokens() throws Exception {
        testData.institution();
        testData.admin();
        String token = adminToken();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"BrandNew456"}
                                """.formatted(TestData.ADMIN_PASSWORD)))
                .andExpect(status().isOk());

        // The token issued before the change is no longer honoured.
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isUnauthorized());

        String newToken = loginToken(TestData.ADMIN_USERNAME, "BrandNew456");
        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(newToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(newToken))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"short"}
                                """.formatted("BrandNew456")))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("change-password refuses a wrong current password and a weak new password")
    void changePasswordValidation() throws Exception {
        testData.institution();
        testData.admin();
        String token = adminToken();

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"NotThePassword1","newPassword":"AnotherPass123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.fieldErrors.currentPassword").isNotEmpty());

        mockMvc.perform(post("/api/v1/auth/change-password")
                        .header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("""
                                {"currentPassword":"%s","newPassword":"alllettersono"}
                                """.formatted(TestData.ADMIN_PASSWORD)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors.newPassword").isNotEmpty());
    }

    @Test
    @DisplayName("forgot-password always answers the same way, whether or not the account exists")
    void forgotPasswordDoesNotLeakAccountExistence() throws Exception {
        testData.institution();
        testData.admin();

        String known = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"admin"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String unknown = mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"does-not-exist"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(known).path("data").path("message").asText())
                .isEqualTo(objectMapper.readTree(unknown).path("data").path("message").asText());
    }

    @Test
    @DisplayName("a reset token can be used once, then becomes invalid")
    void resetPasswordTokenIsSingleUse() throws Exception {
        testData.institution();
        testData.admin();

        String rawToken = issueResetToken(TestData.ADMIN_USERNAME);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken, "newPassword", "ResetPass789"))))
                .andExpect(status().isOk());

        // Single use: the same token must not work twice.
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken, "newPassword", "AnotherPass789"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        String token = loginToken(TestData.ADMIN_USERNAME, "ResetPass789");
        assertThat(token).isNotBlank();
    }

    @Test
    @DisplayName("login and failed login are audited")
    void authenticationEventsAreAudited() throws Exception {
        testData.institution();
        testData.admin();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType("application/json")
                .content("""
                        {"loginId":"admin","password":"WrongPass123"}
                        """)).andExpect(status().isUnauthorized());
        loginToken(TestData.ADMIN_USERNAME, TestData.ADMIN_PASSWORD);

        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(2);
    }

    /**
     * Signs in and returns the refresh token specifically. {@code loginToken} returns
     * the access token, which is not valid on the refresh endpoint.
     */
    private String loginRefreshToken(String username, String password) throws Exception {
        return loginTokens(username, password).refreshToken();
    }

    /** Signs in once and returns both tokens, which must come from the same session. */
    private Tokens loginTokens(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("loginId", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(body).path("data");
        return new Tokens(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private record Tokens(String accessToken, String refreshToken) {
    }

    /**
     * Triggers a real forgot-password request and returns the token from the captured
     * notification, so the test exercises the same path a user would.
     */
    private String issueResetToken(String username) throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("identifier", username))))
                .andExpect(status().isOk());
        NotificationService.SentMessage message = notificationService.getLastMessage();
        assertThat(message).as("reset notification").isNotNull();
        return message.field("Reset token");
    }
}