package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminAiAndAvatarControllerTest extends AdminControllerTestBase {

    @Test
    void avatar_and_ai_endpoints_behave() {
        String token = token();

        var avatar = authed(token, HttpMethod.POST, "/api/admin/user/avatar", Map.of(
                "path", "/avatars/admin.png"
        ));
        assert2xx(avatar);
        Map<?, ?> avatarData = (Map<?, ?>) avatar.getBody().get("data");
        assertThat(avatarData.get("avatar")).isInstanceOf(Map.class);

        var translations = authed(token, HttpMethod.POST, "/api/admin/ai/translations", Map.of(
                "text", "Hello world",
                "languages", java.util.List.of("en", "de")
        ));
        assert2xx(translations);
        assertThat(((Map<?, ?>) translations.getBody().get("data")).get("translations")).isInstanceOf(Map.class);

        var rewrite = authed(token, HttpMethod.POST, "/api/admin/ai/optimise-rewrite", Map.of(
                "text", "Hello world",
                "tone", "formal"
        ));
        assert2xx(rewrite);
        assertThat(((Map<?, ?>) rewrite.getBody().get("data")).get("rewritten")).isEqualTo("Please note: Hello world");
    }

    @Test
    void ai_endpoints_require_ai_manage_permission() {
        String email = "ai-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Ai",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "ai_manager");
        assignPermissionToRole("ai_manager", "ai.manage");
        assignPermissionToRole("ai_manager", "users.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.POST, "/api/admin/ai/translations", Map.of("text", "Hello")).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/user/avatar", Map.of("path", "/avatars/ok.png")).getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}
