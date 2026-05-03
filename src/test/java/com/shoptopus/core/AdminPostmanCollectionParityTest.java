package com.shoptopus.core;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminPostmanCollectionParityTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void postman_admin_method_aliases_are_mapped_and_reachable() {
        String token = token();
        String id = UUID.randomUUID().toString();
        List<RouteCall> calls = List.of(
                new RouteCall(HttpMethod.POST, "/api/admin/product/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/product-category/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/product-tag/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/product-attribute/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/product-attribute/" + id + "/option/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/order/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/payment/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/voucher-code/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/delivery-type/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/delivery-type/" + id + "/delivery-rule/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rule/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/user/avatar"),
                new RouteCall(HttpMethod.POST, "/api/admin/user/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/banner/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/file/" + id),
                new RouteCall(HttpMethod.POST, "/api/admin/file/" + id + "/download"),
                new RouteCall(HttpMethod.POST, "/api/admin/note/" + id),
                new RouteCall(HttpMethod.DELETE, "/api/admin/files/" + id)
        );

        HttpHeaders authHeaders = new HttpHeaders();
        authHeaders.setBearerAuth(token);

        for (RouteCall call : calls) {
            var res = restTemplate.exchange(call.path, call.method, new HttpEntity<>(Map.of("status", 1), authHeaders), Map.class);
            assertThat(!res.getStatusCode().is5xxServerError())
                    .as(call.method + " " + call.path + " should be mapped (non-5xx), got " + res.getStatusCode() + " body=" + res.getBody())
                    .isTrue();
        }

        HttpHeaders noAuthHeaders = new HttpHeaders();
        for (RouteCall call : calls) {
            var res = restTemplate.exchange(call.path, call.method, new HttpEntity<>(Map.of("status", 1), noAuthHeaders), Map.class);
            assertThat(res.getStatusCode().is4xxClientError())
                    .as(call.method + " " + call.path + " unauth should be blocked but returned " + res.getStatusCode())
                    .isTrue();
        }
    }

    private String token() {
        String email = "admin-postman-parity-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Admin",
                "last_name", "Postman",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
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

    private record RouteCall(HttpMethod method, String path) { }
}
