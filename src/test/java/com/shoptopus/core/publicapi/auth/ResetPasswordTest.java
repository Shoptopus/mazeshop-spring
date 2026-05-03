package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ResetPasswordTest extends AuthApiTestBase {

    @Test
    void authenticated_users_cannot_update_their_password() {
        String token = registerAndLogin("reset-auth-" + UUID.randomUUID() + "@example.com");
        var res = exchange(HttpMethod.POST, "/api/auth/reset-password", token, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void it_has_all_required_fields() {
        var res = restTemplate.postForEntity("/api/auth/reset-password", Map.of(), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("developer_message"))).contains("required");
    }

    @Test
    void it_updates_the_password_successfully() {
        String email = "reset-ok-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Reset",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        String resetToken = UUID.randomUUID().toString();
        jdbc.update("insert into password_reset_tokens(email, token, created_at) values (?, ?, now())", email, resetToken);

        var res = restTemplate.postForEntity("/api/auth/reset-password", Map.of(
                "email", email,
                "token", resetToken,
                "password", "newpassword123",
                "password_confirmation", "newpassword123"
        ), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.get("message")).isEqualTo("Password successfully updated!");

        Integer tokenRows = jdbc.queryForObject("select count(*) from password_reset_tokens where lower(email)=lower(?)", Integer.class, email);
        assertThat(tokenRows).isEqualTo(0);

        var login = restTemplate.postForEntity("/api/auth/login", Map.of("email", email, "password", "newpassword123"), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void email_address_must_exist_for_the_given_token() {
        String email = "reset-invalid-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Reset",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        String resetToken = UUID.randomUUID().toString();
        jdbc.update("insert into password_reset_tokens(email, token, created_at) values (?, ?, now())", email, resetToken);

        var res = restTemplate.postForEntity("/api/auth/reset-password", Map.of(
                "email", "missing-" + UUID.randomUUID() + "@example.com",
                "token", resetToken,
                "password", "newpassword123",
                "password_confirmation", "newpassword123"
        ), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("This password reset token is invalid.");
        Integer tokenRows = jdbc.queryForObject("select count(*) from password_reset_tokens where lower(email)=lower(?)", Integer.class, email);
        assertThat(tokenRows).isEqualTo(1);
    }

    @Test
    void token_expires_in_60_minutes() {
        String email = "reset-expired-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Reset",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        String resetToken = UUID.randomUUID().toString();
        jdbc.update("insert into password_reset_tokens(email, token, created_at) values (?, ?, now() - interval '61 minutes')", email, resetToken);

        var res = restTemplate.postForEntity("/api/auth/reset-password", Map.of(
                "email", email,
                "token", resetToken,
                "password", "newpassword123",
                "password_confirmation", "newpassword123"
        ), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("This password reset token has expired.");
        Integer tokenRows = jdbc.queryForObject("select count(*) from password_reset_tokens where lower(email)=lower(?)", Integer.class, email);
        assertThat(tokenRows).isEqualTo(0);
    }
}
