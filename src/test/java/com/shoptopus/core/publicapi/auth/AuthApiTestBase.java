package com.shoptopus.core.publicapi.auth;

import com.shoptopus.core.IntegrationTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

abstract class AuthApiTestBase extends IntegrationTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String registerAndLogin(String email) {
        var register = restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Auth",
                "last_name", "Tester",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assertThat(register.getStatusCode().is2xxSuccessful()).isTrue();

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    protected ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    protected UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
                "select id::text from users where lower(email) = lower(?) and deleted_at is null",
                String.class,
                email
        ));
    }
}

