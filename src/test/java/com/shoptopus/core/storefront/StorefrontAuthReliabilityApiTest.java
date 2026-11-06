package com.shoptopus.core.storefront;

import static org.assertj.core.api.Assertions.assertThat;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.mail.EmailDeliveryService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

class StorefrontAuthReliabilityApiTest extends IntegrationTestBase {
    @Autowired TestRestTemplate http;
    @Autowired JdbcTemplate jdbc;
    @MockBean EmailDeliveryService emailDeliveryService;
    @LocalServerPort int port;

    @Test
    void malformed_registration_returns_validation_error_instead_of_server_error() {
        var response =
                request(
                        HttpMethod.POST,
                        "/api/storefront/auth/register",
                        Map.of(
                                "first_name",
                                "",
                                "last_name",
                                "",
                                "email",
                                "invalid",
                                "password",
                                "x"),
                        null);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void signup_does_not_claim_an_unconfirmed_email_is_verified() {
        var response = register();
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        var auth = map(data(response).get("auth"));
        var user = map(auth.get("user"));
        assertThat(user.get("is_verified")).isEqualTo(false);
        assertThat(
                        jdbc.queryForObject(
                                "select email_verified_at is null from users where id=?::uuid",
                                Boolean.class,
                                user.get("id")))
                .isTrue();
    }

    @Test
    void logout_revokes_the_token_and_a_second_login_has_an_independent_session() {
        var first = map(data(register()).get("auth"));
        String token = String.valueOf(first.get("token"));
        var user = map(first.get("user"));
        String next =
                String.valueOf(
                        map(data(request(
                                                HttpMethod.POST,
                                                "/api/storefront/auth/login",
                                                Map.of(
                                                        "email",
                                                        user.get("email"),
                                                        "password",
                                                        "TestOnly-password-2026!"),
                                                null))
                                        .get("auth"))
                                .get("token"));
        assertThat(next).isNotEqualTo(token);
        assertThat(
                        request(HttpMethod.POST, "/api/storefront/auth/logout", Map.of(), token)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(request(HttpMethod.GET, "/api/storefront/auth/me", null, token).getStatusCode())
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(request(HttpMethod.GET, "/api/storefront/auth/me", null, next).getStatusCode())
                .isEqualTo(HttpStatus.OK);
    }

    @Test
    void confirmation_email_contains_an_expiring_secret_link_and_confirmation_is_idempotent() {
        var user = map(map(data(register()).get("auth")).get("user"));
        String html =
                jdbc.queryForObject(
                        "select html_content from system_mail_outbox where recipient=? order by"
                            + " created_at desc limit 1",
                        String.class,
                        user.get("email"));
        var matcher = java.util.regex.Pattern.compile("token=([a-zA-Z0-9-]+)").matcher(html);
        assertThat(matcher.find()).isTrue();
        String secret = matcher.group(1);
        var confirm =
                request(
                        HttpMethod.GET,
                        "/api/storefront/auth/verify-email?token=" + secret,
                        null,
                        null);
        assertThat(confirm.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(
                        request(
                                        HttpMethod.GET,
                                        "/api/storefront/auth/verify-email?token=" + secret,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.OK);
        assertThat(
                        jdbc.queryForObject(
                                "select email_verified_at is not null from users where id=?::uuid",
                                Boolean.class,
                                user.get("id")))
                .isTrue();
        assertThat(
                        jdbc.queryForObject(
                                "select count(*) from access_tokens where token=?",
                                Integer.class,
                                secret))
                .isZero();
    }

    @Test
    void invalid_or_expired_confirmation_links_cannot_verify_an_account() {
        var user = map(map(data(register()).get("auth")).get("user"));
        String html =
                jdbc.queryForObject(
                        "select html_content from system_mail_outbox where recipient=? order by"
                            + " created_at desc limit 1",
                        String.class,
                        user.get("email"));
        var matcher = java.util.regex.Pattern.compile("token=([a-zA-Z0-9-]+)").matcher(html);
        assertThat(matcher.find()).isTrue();
        String secret = matcher.group(1);
        jdbc.update(
                "update access_tokens set expiry=now()-interval '1 minute' where"
                    + " accessable_type='EmailVerification' and accessable_id=?",
                String.valueOf(user.get("id")));
        assertThat(
                        request(
                                        HttpMethod.GET,
                                        "/api/storefront/auth/verify-email?token=" + secret,
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.GONE);
        assertThat(
                        request(
                                        HttpMethod.GET,
                                        "/api/storefront/auth/verify-email?token=not-a-token",
                                        null,
                                        null)
                                .getStatusCode())
                .isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(
                        jdbc.queryForObject(
                                "select email_verified_at is null from users where id=?::uuid",
                                Boolean.class,
                                user.get("id")))
                .isTrue();
    }

    private ResponseEntity<Map> register() {
        return request(
                HttpMethod.POST,
                "/api/storefront/auth/register",
                Map.of(
                        "first_name",
                        "Checkout",
                        "last_name",
                        "Test",
                        "email",
                        "auth-qa-" + UUID.randomUUID() + "@example.invalid",
                        "password",
                        "TestOnly-password-2026!",
                        "password_confirmation",
                        "TestOnly-password-2026!"),
                null);
    }

    private ResponseEntity<Map> request(HttpMethod method, String path, Object body, String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) headers.setBearerAuth(token);
        return http.exchange(
                "http://localhost:" + port + path,
                method,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private Map<String, Object> data(ResponseEntity<Map> response) {
        return map(response.getBody().get("data"));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
