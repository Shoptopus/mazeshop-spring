package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SocialLoginTargetUrlTest extends AuthApiTestBase {

    @Test
    void it_accepts_only_predefined_providers() {
        var invalid = exchange(HttpMethod.GET, "/api/auth/login/invalidprovider", null, null);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void it_returns_target_url_for_facebook() {
        var facebook = exchange(HttpMethod.GET, "/api/auth/login/facebook", null, null);
        assertThat(facebook.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) facebook.getBody().get("data");
        assertThat(String.valueOf(data.get("targetUrl"))).contains("/oauth/facebook");
    }

    @Test
    void it_returns_target_url_for_google() {
        var google = exchange(HttpMethod.GET, "/api/auth/login/google", null, null);
        assertThat(google.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) google.getBody().get("data");
        assertThat(String.valueOf(data.get("targetUrl"))).contains("/oauth/google");
    }
}

