package com.shoptopus.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminRouteParityTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    private String token() {
        String email = "admin-parity-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "firstName", "Admin",
                "lastName", "Tester",
                "email", email,
                "password", "password123"
        ), Map.class);
        assignRole(email, "super_admin");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    private void assignRole(String email, String roleName) {
        jdbc.update("insert into roles(name, created_at, updated_at) values (?, now(), now()) on conflict (name) do nothing", roleName);
        Integer roleId = jdbc.queryForObject("select id from roles where name = ? limit 1", Integer.class, roleName);
        String userId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, email);
        jdbc.update("insert into model_has_roles(role_id, user_id) values (?, ?::uuid) on conflict do nothing", roleId, userId);
    }

    @Test
    void key_admin_endpoints_are_reachable() {
        String token = token();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        for (String path : new String[]{
                "/api/admin/products",
                "/api/admin/product-categories",
                "/api/admin/product-tags",
                "/api/admin/product-attributes",
                "/api/admin/orders",
                "/api/admin/payments",
                "/api/admin/users",
                "/api/admin/customers",
                "/api/admin/carts",
                "/api/admin/ratings",
                "/api/admin/banners",
                "/api/admin/dashboard",
                "/api/admin/roles",
                "/api/admin/permissions"
        }) {
            var res = restTemplate.exchange(path, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
            assertThat(res.getStatusCode().is2xxSuccessful()).as(path + " -> " + res.getStatusCode() + " body=" + res.getBody()).isTrue();
        }
    }
}
