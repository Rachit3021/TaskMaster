package com.taskflow;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AuthIntegrationTest extends IntegrationTestSupport {

    @Test
    void registrationLoginAndProfileManagement() throws Exception {
        TestUser user = registerUser("Ada Lovelace");

        JsonNode me = getJson("/api/v1/users/me", user.accessToken(), 200);
        assertThat(me.path("email").asText()).isEqualTo(user.email());
        assertThat(me.path("name").asText()).isEqualTo("Ada Lovelace");

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("name", "Ada L.");
        update.put("bio", "Backend engineer");
        update.put("avatarUrl", "https://example.com/ada.png");
        JsonNode updated = patchJson("/api/v1/users/me", update, user.accessToken(), 200);
        assertThat(updated.path("name").asText()).isEqualTo("Ada L.");
        assertThat(updated.path("bio").asText()).isEqualTo("Backend engineer");

        JsonNode login = postJson("/api/v1/auth/login",
                Map.of("email", user.email().toUpperCase(), "password", user.password()), null, 200);
        assertThat(login.path("accessToken").asText()).isNotBlank();
        assertThat(login.path("user").path("name").asText()).isEqualTo("Ada L.");

        JsonNode wrongPassword = postJson("/api/v1/auth/login",
                Map.of("email", user.email(), "password", "WrongPassword1"), null, 401);
        assertThat(wrongPassword.path("code").asText()).isEqualTo("UNAUTHORIZED");
    }

    @Test
    void registrationRejectsWeakPasswordsAndDuplicateEmails() throws Exception {
        JsonNode invalid = postJson("/api/v1/auth/register",
                Map.of("name", "A", "email", "not-an-email", "password", "short"), null, 400);
        assertThat(invalid.path("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(invalid.path("fieldErrors").has("email")).isTrue();
        assertThat(invalid.path("fieldErrors").has("password")).isTrue();

        TestUser existing = registerUser("Existing User");
        JsonNode duplicate = postJson("/api/v1/auth/register",
                Map.of("name", "Copy Cat", "email", existing.email(), "password", "Password123"),
                null, 409);
        assertThat(duplicate.path("code").asText()).isEqualTo("CONFLICT");
    }

    @Test
    void refreshTokenRotationRejectsReuse() throws Exception {
        TestUser user = registerUser("Refresh User");

        JsonNode rotated = postJson("/api/v1/auth/refresh",
                Map.of("refreshToken", user.refreshToken()), null, 200);
        String newRefreshToken = rotated.path("refreshToken").asText();
        assertThat(newRefreshToken).isNotBlank().isNotEqualTo(user.refreshToken());
        assertThat(rotated.path("tokenType").asText()).isEqualTo("Bearer");

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()), null, 401);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", newRefreshToken), null, 200);
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        TestUser user = registerUser("Logout User");

        postJson("/api/v1/auth/logout", Map.of("refreshToken", user.refreshToken()), null, 204);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()), null, 401);
    }

    @Test
    void logoutAllRevokesEverySession() throws Exception {
        TestUser user = registerUser("Global Logout");
        JsonNode secondLogin = postJson("/api/v1/auth/login",
                Map.of("email", user.email(), "password", user.password()), null, 200);
        String secondRefresh = secondLogin.path("refreshToken").asText();

        postJson("/api/v1/auth/logout-all", null, user.accessToken(), 204);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()), null, 401);
        postJson("/api/v1/auth/refresh", Map.of("refreshToken", secondRefresh), null, 401);
    }

    @Test
    void changingPasswordInvalidatesExistingSessions() throws Exception {
        TestUser user = registerUser("Password Changer");

        putJson("/api/v1/users/me/password",
                Map.of("currentPassword", user.password(), "newPassword", "BrandNew123"),
                user.accessToken(), 204);

        postJson("/api/v1/auth/refresh", Map.of("refreshToken", user.refreshToken()), null, 401);
        postJson("/api/v1/auth/login",
                Map.of("email", user.email(), "password", user.password()), null, 401);
        postJson("/api/v1/auth/login",
                Map.of("email", user.email(), "password", "BrandNew123"), null, 200);
    }

    @Test
    void protectedEndpointsRequireAuthentication() throws Exception {
        getJson("/api/v1/users/me", null, 401);
        getJson("/api/v1/teams", null, 401);
        getJson("/api/v1/tasks/assigned-to-me", "not-a-valid-token", 401);
    }
}
