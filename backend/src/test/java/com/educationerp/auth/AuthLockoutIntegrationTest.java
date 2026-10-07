package com.educationerp.auth;

import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Account lockout and inactivity must be enforced not just at the next sign-in but
 * against every token already in circulation: a locked account cannot refresh its
 * sessions, and disabling an account has to be immediate, not deferred to token expiry.
 */
class AuthLockoutIntegrationTest extends IntegrationTest {

    private record Tokens(String access, String refresh) {}

    private Tokens loginPair(String username, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("loginId", username, "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        var data = objectMapper.readTree(body).path("data");
        return new Tokens(data.path("accessToken").asText(), data.path("refreshToken").asText());
    }

    private UUID createReceptionist(String username) throws Exception {
        String created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"RecPass1234","displayName":"Receptionist","role":"RECEPTIONIST"}
                                """.formatted(username)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(created).path("data").path("id").asText());
    }

    private void lockAccount(String username) throws Exception {
        for (int attempt = 0; attempt < 5; attempt++) {
            mockMvc.perform(post("/api/v1/auth/login")
                    .contentType("application/json")
                    .content(objectMapper.writeValueAsString(Map.of("loginId", username, "password", "WrongPass123"))));
        }
    }

    @Test
    @DisplayName("a locked account cannot refresh its session and its access token stops working")
    void lockedAccountLosesAllTokens() throws Exception {
        createReceptionist("lokia");
        Tokens pair = loginPair("lokia", "RecPass1234");

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(pair.access())))
                .andExpect(status().isOk());

        lockAccount("lokia");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", pair.refresh()))))
                .andExpect(status().is(423))
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(pair.access())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("an admin disabling an account is immediate for tokens already issued")
    void statusChangeKillsTokensImmediately() throws Exception {
        UUID id = createReceptionist("offline");
        Tokens pair = loginPair("offline", "RecPass1234");

        mockMvc.perform(put("/api/v1/admin/users/" + id)
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"displayName":"Receptionist","role":"RECEPTIONIST","status":"INACTIVE"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me").header("Authorization", bearer(pair.access())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", pair.refresh()))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_INACTIVE"));
    }

    @Test
    @DisplayName("an active session can still be refreshed")
    void activeSessionCanRefresh() throws Exception {
        createReceptionist("rolly");
        Tokens pair = loginPair("rolly", "RecPass1234");

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of("refreshToken", pair.refresh()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }
}