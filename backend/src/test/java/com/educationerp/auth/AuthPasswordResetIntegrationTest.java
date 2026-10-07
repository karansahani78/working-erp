package com.educationerp.auth;

import com.educationerp.common.notification.NotificationService;
import com.educationerp.support.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The password reset link must only ever travel to the email the account was created
 * with. A caller who knows a username must not be able to redirect the token to an
 * inbox they control.
 */
class AuthPasswordResetIntegrationTest extends IntegrationTest {

    @Autowired
    private NotificationService notificationService;

    private UUID createReceptionist(String username, String email) throws Exception {
        String emailField = email == null ? "" : ",\"email\":\"" + email + "\"";
        String created = mockMvc.perform(post("/api/v1/admin/users")
                        .header("Authorization", bearer(adminToken()))
                        .contentType("application/json")
                        .content("""
                                {"username":"%s","password":"RecPass1234","displayName":"Receptionist"%s,
                                 "role":"RECEPTIONIST"}
                                """.formatted(username, emailField)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(created).path("data").path("id").asText());
    }

    @Test
    @DisplayName("the reset link ignores a supplied email and goes to the registered address")
    void resetLinkCannotBeRedirected() throws Exception {
        createReceptionist("pill", "pill@sunrise.edu.np");

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"pill","email":"attacker@evil.example"}
                                """))
                .andExpect(status().isOk());

        NotificationService.SentMessage message = notificationService.getLastMessage();
        assertThat(message).as("reset notification").isNotNull();
        assertThat(message.to()).isEqualTo("pill@sunrise.edu.np");
        assertThat(message.to()).isNotEqualTo("attacker@evil.example");

        // The token that was issued works end to end, so a legitimate owner still resets.
        String rawToken = message.field("Reset token");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType("application/json")
                        .content(objectMapper.writeValueAsString(Map.of(
                                "token", rawToken, "newPassword", "FreshPass789"))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("requesting a reset for an account with no email answers the same way and mails nothing")
    void noEmailOnFileIsSilent() throws Exception {
        createReceptionist("mumMum", "mum@sunrise.edu.np");
        createReceptionist("mum", null);

        NotificationService.SentMessage before = notificationService.getLastMessage();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"does-not-exist","email":"somewhere@evil.example"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType("application/json")
                        .content("""
                                {"identifier":"mum","email":"somewhere@evil.example"}
                                """))
                .andExpect(status().isOk());

        // Nothing new was mailed: the account has no registered address to receive the link.
        assertThat(notificationService.getLastMessage()).isEqualTo(before);
    }
}