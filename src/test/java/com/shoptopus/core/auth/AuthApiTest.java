package com.shoptopus.core.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoptopus.core.IntegrationTestBase;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

class AuthApiTest extends IntegrationTestBase {

    @Autowired private TestRestTemplate restTemplate;

    @Test
    void register_login_and_me_work() {
        String email = "jane-" + UUID.randomUUID() + "@example.com";
        var registerReq =
                Map.of(
                        "firstName", "Jane",
                        "lastName", "Doe",
                        "email", email,
                        "password", "password123");

        var registerRes = restTemplate.postForEntity("/api/auth/register", registerReq, Map.class);
        assertThat(registerRes.getStatusCode().is2xxSuccessful()).isTrue();

        var loginReq = Map.of("email", email, "password", "password123");
        var loginRes = restTemplate.postForEntity("/api/auth/login", loginReq, Map.class);
        assertThat(loginRes.getStatusCode().is2xxSuccessful()).isTrue();

        var data = (Map<?, ?>) loginRes.getBody().get("data");
        var auth = (Map<?, ?>) data.get("auth");
        var token = (String) auth.get("token");
        assertThat(token).isNotBlank();

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        var details =
                restTemplate.exchange(
                        "/api/auth/details", HttpMethod.GET, new HttpEntity<>(headers), Map.class);
        assertThat(details.getStatusCode().is2xxSuccessful()).isTrue();
    }

    @Test
    void register_validation_and_duplicate_email_are_enforced() {
        var missing = restTemplate.postForEntity("/api/auth/register", Map.of(), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().get("errors"))
                .asString()
                .contains("firstName", "lastName", "email", "password");

        String email = "dup-" + UUID.randomUUID() + "@example.com";
        var req =
                Map.of(
                        "firstName", "Dup",
                        "lastName", "User",
                        "email", email,
                        "password", "password123");
        var first = restTemplate.postForEntity("/api/auth/register", req, Map.class);
        assertThat(first.getStatusCode().is2xxSuccessful()).isTrue();

        var duplicate = restTemplate.postForEntity("/api/auth/register", req, Map.class);
        assertThat(duplicate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(duplicate.getBody().get("user_message")).isEqualTo("Email already in use");
    }

    @Test
    void login_validates_and_returns_expected_errors() {
        var invalid =
                restTemplate.postForEntity(
                        "/api/auth/login",
                        Map.of("email", "not-an-email", "password", "x"),
                        Map.class);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalid.getBody().get("errors")).asString().contains("email");

        var unknown =
                restTemplate.postForEntity(
                        "/api/auth/login",
                        Map.of(
                                "email",
                                "unknown-" + UUID.randomUUID() + "@example.com",
                                "password",
                                "password123"),
                        Map.class);
        assertThat(unknown.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unknown.getBody().get("user_message"))
                .isEqualTo("Incorrect email or password. Please try again.");
        assertThat(unknown.getBody().get("developer_message")).isEqualTo("User not found.");

        String email = "known-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity(
                "/api/auth/register",
                Map.of(
                        "firstName", "Known",
                        "lastName", "User",
                        "email", email,
                        "password", "password123"),
                Map.class);
        var wrongPassword =
                restTemplate.postForEntity(
                        "/api/auth/login",
                        Map.of("email", email, "password", "wrong-password"),
                        Map.class);
        assertThat(wrongPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(wrongPassword.getBody().get("user_message"))
                .isEqualTo("Incorrect email or password. Please try again.");
        assertThat(wrongPassword.getBody().get("developer_message")).isEqualTo("Hash check fail");
    }

    @Test
    void details_and_logout_require_authentication() {
        var details = restTemplate.getForEntity("/api/auth/details", Map.class);
        assertThat(details.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(details.getBody().get("user_message")).isEqualTo("Unauthenticated");
        assertThat(details.getBody().get("developer_message")).isEqualTo("Authentication required");
        assertThat(((Number) details.getBody().get("error_code")).intValue()).isEqualTo(1101);

        var logout = restTemplate.postForEntity("/api/auth/logout", Map.of(), Map.class);
        assertThat(logout.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void register_accepts_snake_case_and_validates_password_confirmation_when_present() {
        String email = "snake-" + UUID.randomUUID() + "@example.com";
        var snake =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "first_name", "Snake",
                                "last_name", "Case",
                                "email", email,
                                "password", "password123",
                                "password_confirmation", "password123"),
                        Map.class);
        assertThat(snake.getStatusCode().is2xxSuccessful()).isTrue();
        var user =
                (Map<?, ?>)
                        ((Map<?, ?>) ((Map<?, ?>) snake.getBody().get("data")).get("auth"))
                                .get("user");
        assertThat(user.get("first_name")).isEqualTo("Snake");
        assertThat(user.get("last_name")).isEqualTo("Case");
        assertThat(user.get("is_verified")).isEqualTo(false);

        var mismatch =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "first_name", "Mismatch",
                                "last_name", "User",
                                "email", "mismatch-" + UUID.randomUUID() + "@example.com",
                                "password", "password123",
                                "password_confirmation", "password124"),
                        Map.class);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(mismatch.getBody().get("developer_message"))
                .isEqualTo("Password confirmation does not match");
    }
}
