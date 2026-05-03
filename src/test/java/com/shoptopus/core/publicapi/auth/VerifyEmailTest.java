package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class VerifyEmailTest extends AuthApiTestBase {

    @Test
    void it_requires_a_valid_url() {
        var res = exchange(org.springframework.http.HttpMethod.GET, "/api/auth/verify/" + UUID.randomUUID(), null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("url"))).contains("Invalid/Expired url provided.");
    }

    @Test
    void users_email_is_marked_verified_upon_successful_verification() {
        String email = "verify-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Verify",
                "last_name", "User",
                "email", email,
                "password", "password123"
        ), Map.class);
        UUID userId = userIdByEmail(email);
        jdbc.update("update users set email_verified_at = null where id = ?::uuid", userId.toString());

        String hash = sha1(email);
        var res = exchange(org.springframework.http.HttpMethod.GET, "/api/auth/verify/" + userId + "?hash=" + hash, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        OffsetDateTime verifiedAt = jdbc.queryForObject(
                "select email_verified_at from users where id = ?::uuid",
                OffsetDateTime.class,
                userId.toString()
        );
        assertThat(verifiedAt).isNotNull();
    }

    private String sha1(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

