package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SendPasswordResetTest extends AuthApiTestBase {

    @Test
    void authenticated_users_cannot_reset_their_password() {
        String token = registerAndLogin("forgot-auth-" + UUID.randomUUID() + "@example.com");
        var res = exchange(HttpMethod.POST, "/api/auth/forgot-password", token, Map.of("email", "ignored@example.com"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void it_has_required_fields() {
        var res = restTemplate.postForEntity("/api/auth/forgot-password", Map.of(), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void it_sends_the_reset_link_successfully_when_user_exists() {
        String email = "forgot-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Forgot",
                "last_name", "User",
                "email", email,
                "password", "password123"
        ), Map.class);

        Integer before = jdbc.queryForObject("select count(*) from password_reset_tokens where lower(email)=lower(?)", Integer.class, email);
        assertThat(before).isEqualTo(0);

        var res = restTemplate.postForEntity("/api/auth/forgot-password", Map.of("email", email), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.get("message")).isEqualTo("If the email is in our system, we have e-mailed your password reset link!");

        Integer after = jdbc.queryForObject("select count(*) from password_reset_tokens where lower(email)=lower(?)", Integer.class, email);
        assertThat(after).isEqualTo(1);
    }
}
