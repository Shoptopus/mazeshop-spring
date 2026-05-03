package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginCallbackTest extends AuthApiTestBase {

    @Test
    void callback_accepts_supported_providers() {
        var google = restTemplate.postForEntity("/api/auth/login/google/callback", Map.of("code", "abc"), Map.class);
        assertThat(google.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) google.getBody().get("data");
        assertThat(data.get("provider")).isEqualTo("google");

        var facebook = restTemplate.postForEntity("/api/auth/login/facebook/callback", Map.of("code", "abc"), Map.class);
        assertThat(facebook.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void callback_rejects_unsupported_provider() {
        var res = restTemplate.postForEntity("/api/auth/login/invalid/callback", Map.of("code", "abc"), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Provider is invalid.");
    }
}
