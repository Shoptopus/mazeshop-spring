package com.shoptopus.core.publicapi.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

class ResendVerificationEmailTest extends AuthApiTestBase {

    @Test
    void it_has_required_fields_when_email_not_provided() {
        var res = restTemplate.postForEntity("/api/auth/verify/resend", Map.of(), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void it_does_not_reveal_whether_an_email_address_exists() {
        var res =
                restTemplate.postForEntity(
                        "/api/auth/verify/resend",
                        Map.of("email", "missing-" + UUID.randomUUID() + "@example.com"),
                        Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void it_returns_success_if_email_already_verified() {
        String email = "verified-" + UUID.randomUUID() + "@example.com";
        registerAndLogin(email);
        jdbc.update("update users set email_verified_at=now() where lower(email)=lower(?)", email);

        var res =
                restTemplate.postForEntity(
                        "/api/auth/verify/resend", Map.of("email", email), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.get("message"))
                .isEqualTo("If confirmation is needed, a confirmation email has been sent.");
    }

    @Test
    void it_resends_verification_email_for_unverified_user() {
        String email = "unverified-" + UUID.randomUUID() + "@example.com";
        UUID userId = userIdByEmailAfterRegister(email);
        jdbc.update(
                "update users set email_verified_at = null where id = ?::uuid", userId.toString());

        var res =
                restTemplate.postForEntity(
                        "/api/auth/verify/resend", Map.of("email", email), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.get("message"))
                .isEqualTo("If confirmation is needed, a confirmation email has been sent.");
    }

    @Test
    void authenticated_users_can_resend_without_email_payload() {
        String email = "resend-auth-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var res = exchange(HttpMethod.POST, "/api/auth/verify/resend", token, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.get("message"))
                .isEqualTo("If confirmation is needed, a confirmation email has been sent.");
    }

    private UUID userIdByEmailAfterRegister(String email) {
        restTemplate.postForEntity(
                "/api/auth/register",
                Map.of(
                        "first_name", "Verify",
                        "last_name", "Resend",
                        "email", email,
                        "password", "password123"),
                Map.class);
        return userIdByEmail(email);
    }
}
