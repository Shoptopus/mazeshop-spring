package com.shoptopus.core.publicapi.product;

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

abstract class ProductApiTestBase extends IntegrationTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbc;

    protected String registerAndLogin(String email) {
        var register = restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Product",
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

    protected UUID ensureProduct() {
        String existing = jdbc.query(
                "select id::text from products where deleted_at is null order by created_at asc limit 1",
                rs -> rs.next() ? rs.getString(1) : null
        );
        if (existing != null) {
            return UUID.fromString(existing);
        }

        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into products(id, name, slug, short_description, description, price, status, purchase_count, stock, virtual, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, 0, 0, 100, false, now(), now())",
                id.toString(),
                "{\"en\":\"API Product\"}",
                "api-product-" + id,
                "{\"en\":\"Short\"}",
                "{\"en\":\"Description\"}",
                19.99
        );
        return id;
    }

    protected UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
                "select id::text from users where lower(email)=lower(?) and deleted_at is null",
                String.class,
                email
        ));
    }
}
