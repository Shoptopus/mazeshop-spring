package com.shoptopus.core.publicapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

class RegisterTest extends AuthApiTestBase {

    @Test
    void it_has_all_required_fields() {
        var res = restTemplate.postForEntity("/api/auth/register", Map.of(), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("errors")))
                .contains("firstName", "lastName", "email", "password");
    }

    @Test
    void it_requires_valid_email_password_confirmation_and_min_password_length() {
        var invalidEmail =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "first_name", "Istvan",
                                "last_name", "Lovas",
                                "email", "lolevesgmail",
                                "password", "password",
                                "password_confirmation", "password"),
                        Map.class);
        assertThat(invalidEmail.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(invalidEmail.getBody().get("errors"))).contains("email");

        var confirmationMismatch =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "first_name", "Istvan",
                                "last_name", "Lovas",
                                "email", "no-confirm-" + UUID.randomUUID() + "@example.com",
                                "password", "password123",
                                "password_confirmation", "password124"),
                        Map.class);
        assertThat(confirmationMismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(confirmationMismatch.getBody().get("developer_message"))
                .isEqualTo("Password confirmation does not match");

        var shortPassword =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "first_name", "Istvan",
                                "last_name", "Lovas",
                                "email", "short-pass-" + UUID.randomUUID() + "@example.com",
                                "password", "passwor",
                                "password_confirmation", "passwor"),
                        Map.class);
        assertThat(shortPassword.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(shortPassword.getBody().get("errors"))).contains("password");
    }

    @Test
    void it_saves_user_creates_access_token_creates_cart_and_returns_relevant_data() {
        String email = "register-" + UUID.randomUUID() + "@example.com";
        var res =
                restTemplate.postForEntity(
                        "/api/auth/register",
                        Map.of(
                                "first_name", "Istvan",
                                "last_name", "Lovas",
                                "email", email,
                                "password", "password123",
                                "password_confirmation", "password123"),
                        Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        UUID userId = userIdByEmail(email);
        Integer users =
                jdbc.queryForObject(
                        "select count(*) from users where id = ?::uuid",
                        Integer.class,
                        userId.toString());
        Integer tokens =
                jdbc.queryForObject(
                        "select count(*) from access_tokens where user_id = ?::uuid and type=0",
                        Integer.class,
                        userId.toString());
        Integer carts =
                jdbc.queryForObject(
                        "select count(*) from carts where user_id = ?::uuid",
                        Integer.class,
                        userId.toString());
        assertThat(users).isEqualTo(1);
        assertThat(tokens).isEqualTo(1);
        assertThat(carts).isEqualTo(1);

        var auth = (Map<?, ?>) ((Map<?, ?>) res.getBody().get("data")).get("auth");
        assertThat(auth.get("token")).isNotNull();
        assertThat(auth.get("token_type")).isEqualTo("Bearer");

        var user = (Map<?, ?>) auth.get("user");
        assertThat(user.keySet().stream().map(String::valueOf).toList())
                .contains(
                        "id",
                        "name",
                        "first_name",
                        "last_name",
                        "email",
                        "phone",
                        "avatar",
                        "is_verified",
                        "cart",
                        "notifications",
                        "favorites");
        var cart = (Map<?, ?>) user.get("cart");
        assertThat(cart.keySet().stream().map(String::valueOf).toList())
                .contains("id", "user_id", "ip_address", "user", "products");
    }
}
