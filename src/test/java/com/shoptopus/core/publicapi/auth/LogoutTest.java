package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LogoutTest extends AuthApiTestBase {

    @Test
    void unauthenticated_users_cannot_logout() {
        var res = exchange(HttpMethod.POST, "/api/auth/logout", null, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("user_message")).isEqualTo("Unauthenticated");
    }

    @Test
    void authenticated_users_can_logout() {
        String token = registerAndLogin("logout-" + UUID.randomUUID() + "@example.com");
        var res = exchange(HttpMethod.POST, "/api/auth/logout", token, Map.of());
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
    }
}

