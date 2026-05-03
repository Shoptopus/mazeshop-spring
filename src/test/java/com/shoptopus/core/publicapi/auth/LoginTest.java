package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoginTest extends AuthApiTestBase {

    @Test
    void it_has_all_required_fields_and_requires_valid_email() {
        var required = restTemplate.postForEntity("/api/auth/login", Map.of(), Map.class);
        assertThat(required.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(required.getBody().get("errors"))).contains("email", "password");

        var invalidEmail = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", "lolevesgmail",
                "password", "password"
        ), Map.class);
        assertThat(invalidEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(invalidEmail.getBody().get("errors"))).contains("email");
    }

    @Test
    void it_requires_correct_password_and_returns_expected_errors() {
        String email = "login-errors-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(email);

        var wrongPassword = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "wrong-password"
        ), Map.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongPassword.getBody().get("user_message")).isEqualTo("Incorrect email or password. Please try again.");
        assertThat(wrongPassword.getBody().get("developer_message")).isEqualTo("Hash check fail");

        var unknown = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", "unknown-" + UUID.randomUUID() + "@example.com",
                "password", "password123"
        ), Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody().get("user_message")).isEqualTo("Incorrect email or password. Please try again.");
        assertThat(unknown.getBody().get("developer_message")).isEqualTo("User not found.");
    }

    @Test
    void successful_login_returns_auth_payload_updates_last_seen_and_returns_unread_notifications_only() {
        String email = "login-ok-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(email);
        UUID userId = userIdByEmail(email);

        jdbc.update("""
                insert into notifications(id, user_id, type, title, data, read_at, created_at, updated_at)
                values (?::uuid, ?::uuid, 'competition-entered', 'Unread', '{}'::jsonb, null, now(), now())
                """, UUID.randomUUID().toString(), userId.toString());
        jdbc.update("""
                insert into notifications(id, user_id, type, title, data, read_at, created_at, updated_at)
                values (?::uuid, ?::uuid, 'competition-entered', 'Read', '{}'::jsonb, now(), now(), now())
                """, UUID.randomUUID().toString(), userId.toString());

        OffsetDateTime before = jdbc.queryForObject(
                "select last_seen from users where id = ?::uuid",
                OffsetDateTime.class,
                userId.toString()
        );

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        assertThat(login.getStatusCode()).isEqualTo(HttpStatus.OK);

        var auth = (Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth");
        assertThat(auth.get("token")).isNotNull();
        assertThat(auth.get("token_type")).isEqualTo("Bearer");

        var user = (Map<?, ?>) auth.get("user");
        assertThat(user.keySet().stream().map(String::valueOf).toList()).contains("notifications", "favorites", "cart");
        var notifications = (java.util.List<?>) user.get("notifications");
        assertThat(notifications).hasSize(1);

        OffsetDateTime after = jdbc.queryForObject(
                "select last_seen from users where id = ?::uuid",
                OffsetDateTime.class,
                userId.toString()
        );
        assertThat(after).isNotNull();
        if (before != null) {
            assertThat(after).isAfterOrEqualTo(before);
        }
    }

    @Test
    void users_without_password_are_sent_the_correct_error_response() {
        String email = "login-nopass-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(email);
        jdbc.update("update users set password = null where lower(email) = lower(?)", email);

        var res = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "randompassword"
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("user_message")).isEqualTo("Please reset your password.");
    }
}
