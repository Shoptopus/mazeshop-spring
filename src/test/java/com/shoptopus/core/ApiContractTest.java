package com.shoptopus.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApiContractTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void paged_response_uses_total_records_snake_case() {
        var res = restTemplate.getForEntity("/api/products", Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(res.getBody()).containsKey("total_records");
    }

    @Test
    void validation_error_shape_matches_laravel_style() {
        var res = restTemplate.postForEntity("/api/auth/login", Map.of(), Map.class);
        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        assertThat(res.getBody()).containsKeys("user_message", "developer_message", "errors");
        var errors = (Map<?, ?>) res.getBody().get("errors");
        assertThat(errors.get("email")).isInstanceOfAny(java.util.List.class);
    }

    @Test
    void login_failure_messages_are_explicit() {
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "firstName", "Fail",
                "lastName", "User",
                "email", "fail@example.com",
                "password", "password123"
        ), Map.class);

        var res = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", "fail@example.com",
                "password", "wrongpass"
        ), Map.class);

        assertThat(res.getStatusCode().is4xxClientError()).isTrue();
        assertThat(res.getBody().get("user_message")).isEqualTo("Incorrect email or password. Please try again.");
        assertThat(res.getBody().get("developer_message")).isEqualTo("Hash check fail");
    }
}
