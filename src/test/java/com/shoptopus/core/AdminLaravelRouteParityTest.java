package com.shoptopus.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.client.ResourceAccessException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminLaravelRouteParityTest extends IntegrationTestBase {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Autowired
    private TestRestTemplate restTemplate;
    @Autowired
    private JdbcTemplate jdbc;

    @LocalServerPort
    private int port;

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
    void admin_routes_from_laravel_are_explicitly_mapped() {
        String token = token();
        String id = UUID.randomUUID().toString();
        String role = "super_admin";
        String permission = "products.list";
        Map<String, Object> body = Map.of("ids", List.of(id), "status", "active");

        List<RouteCall> calls = routeCalls(id, role, permission, body);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);

        for (RouteCall call : calls) {
            var res = exchange(call, headers);
            assertThat(!res.getStatusCode().is5xxServerError())
                    .as(call.method + " " + call.path + " should be mapped (non-5xx), got " + res.getStatusCode() + " body=" + res.getBody())
                    .isTrue();
        }
    }

    @Test
    void admin_routes_from_laravel_require_authentication() {
        String id = UUID.randomUUID().toString();
        String role = "super_admin";
        String permission = "products.list";
        Map<String, Object> body = Map.of("ids", List.of(id), "status", "active");
        List<RouteCall> calls = routeCalls(id, role, permission, body);

        HttpHeaders headers = new HttpHeaders();
        for (RouteCall call : calls) {
            var res = exchange(call, headers);
            assertThat(res.getStatusCode().is4xxClientError())
                    .as(call.method + " " + call.path + " should require auth but returned " + res.getStatusCode() + " body=" + res.getBody())
                    .isTrue();
        }
    }

    private List<RouteCall> routeCalls(String id, String role, String permission, Map<String, Object> body) {
        return List.of(
                // GET
                new RouteCall(HttpMethod.GET, "/api/admin/products", null),
                new RouteCall(HttpMethod.GET, "/api/admin/products/summary", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/product/" + id + "/insights", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product/" + id + "/paid-contents", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product/" + id + "/paid-content/" + id + "/download", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product/" + id + "/variants", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product/" + id + "/variant/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-categories", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-categories/select-data", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-category/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-tags", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-tag/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-attributes", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-attribute/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-attribute/" + id + "/options", null),
                new RouteCall(HttpMethod.GET, "/api/admin/product-attribute/" + id + "/option/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/audits", null),
                new RouteCall(HttpMethod.GET, "/api/admin/audit/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/dashboard", null),
                new RouteCall(HttpMethod.GET, "/api/admin/notifications", null),
                new RouteCall(HttpMethod.GET, "/api/admin/orders", null),
                new RouteCall(HttpMethod.GET, "/api/admin/order/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/payments", null),
                new RouteCall(HttpMethod.GET, "/api/admin/payment/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/voucher-codes", null),
                new RouteCall(HttpMethod.GET, "/api/admin/voucher-code/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/delivery-types", null),
                new RouteCall(HttpMethod.GET, "/api/admin/delivery-type/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/delivery-type/" + id + "/delivery-rules", null),
                new RouteCall(HttpMethod.GET, "/api/admin/delivery-type/" + id + "/delivery-rule/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/discount-rules", null),
                new RouteCall(HttpMethod.GET, "/api/admin/discount-rule/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/discount-rule/" + id + "/available-categories", null),
                new RouteCall(HttpMethod.GET, "/api/admin/discount-rule/" + id + "/available-products", null),
                new RouteCall(HttpMethod.GET, "/api/admin/users", null),
                new RouteCall(HttpMethod.GET, "/api/admin/user/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/customers", null),
                new RouteCall(HttpMethod.GET, "/api/admin/customer/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/carts", null),
                new RouteCall(HttpMethod.GET, "/api/admin/cart/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/ratings", null),
                new RouteCall(HttpMethod.GET, "/api/admin/rating/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/banners", null),
                new RouteCall(HttpMethod.GET, "/api/admin/banner/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/files", null),
                new RouteCall(HttpMethod.GET, "/api/admin/file/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/notes", null),
                new RouteCall(HttpMethod.GET, "/api/admin/note/" + id, null),
                new RouteCall(HttpMethod.GET, "/api/admin/roles", null),
                new RouteCall(HttpMethod.GET, "/api/admin/role/" + role + "/permissions", null),
                new RouteCall(HttpMethod.GET, "/api/admin/role/" + role + "/users", null),
                new RouteCall(HttpMethod.GET, "/api/admin/role/" + role + "/available-users", null),
                new RouteCall(HttpMethod.GET, "/api/admin/permissions", null),
                new RouteCall(HttpMethod.GET, "/api/admin/get-users", null),

                // POST
                new RouteCall(HttpMethod.POST, "/api/admin/products/bulk/archive", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product/" + id + "/paid-content", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product/" + id + "/variant", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-categories/bulk/availability", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-category", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-tags/bulk/availability", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-tag", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-attributes/bulk/availability", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-attribute", body),
                new RouteCall(HttpMethod.POST, "/api/admin/product-attribute/" + id + "/option", body),
                new RouteCall(HttpMethod.POST, "/api/admin/report", body),
                new RouteCall(HttpMethod.POST, "/api/admin/reports/overview", body),
                new RouteCall(HttpMethod.POST, "/api/admin/reports/sales", body),
                new RouteCall(HttpMethod.POST, "/api/admin/reports/product-ratings/" + id, body),
                new RouteCall(HttpMethod.POST, "/api/admin/orders/bulk/status", body),
                new RouteCall(HttpMethod.POST, "/api/admin/payments/bulk/status", body),
                new RouteCall(HttpMethod.POST, "/api/admin/payment", body),
                new RouteCall(HttpMethod.POST, "/api/admin/voucher-codes/bulk/expire", body),
                new RouteCall(HttpMethod.POST, "/api/admin/voucher-codes/bulk/start", body),
                new RouteCall(HttpMethod.POST, "/api/admin/voucher-codes/bulk/activate", body),
                new RouteCall(HttpMethod.POST, "/api/admin/voucher-code", body),
                new RouteCall(HttpMethod.POST, "/api/admin/delivery-types/bulk/availability", body),
                new RouteCall(HttpMethod.POST, "/api/admin/delivery-type", body),
                new RouteCall(HttpMethod.POST, "/api/admin/delivery-type/" + id + "/delivery-rule", body),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rules/bulk/expire", body),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rules/bulk/start", body),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rules/bulk/activate", body),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rule", body),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rule/" + id + "/products/" + id, body),
                new RouteCall(HttpMethod.POST, "/api/admin/discount-rule/" + id + "/product-categories/" + id, body),
                new RouteCall(HttpMethod.POST, "/api/admin/user", body),
                new RouteCall(HttpMethod.POST, "/api/admin/user/invite", body),
                new RouteCall(HttpMethod.POST, "/api/admin/ratings/bulk/availability", body),
                new RouteCall(HttpMethod.POST, "/api/admin/ratings/bulk/verified-status", body),
                new RouteCall(HttpMethod.POST, "/api/admin/banner/bulk/availability", body),
                new RouteCall(HttpMethod.POST, "/api/admin/banner", body),
                new RouteCall(HttpMethod.POST, "/api/admin/file", body),
                new RouteCall(HttpMethod.POST, "/api/admin/note", body),
                new RouteCall(HttpMethod.POST, "/api/admin/role", body),
                new RouteCall(HttpMethod.POST, "/api/admin/role/" + role + "/permission/" + permission, body),
                new RouteCall(HttpMethod.POST, "/api/admin/send-email", body),
                new RouteCall(HttpMethod.POST, "/api/admin/ai/translations", body),
                new RouteCall(HttpMethod.POST, "/api/admin/ai/optimise-rewrite", body),
                new RouteCall(HttpMethod.POST, "/api/admin/notifications", body),

                // PATCH
                new RouteCall(HttpMethod.PATCH, "/api/admin/product/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/product/" + id + "/paid-content/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/product/" + id + "/variant/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/product-category/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/product-tag/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/product-attribute/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/product-attribute/" + id + "/option/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/order/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/payment/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/voucher-code/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/delivery-type/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/delivery-type/" + id + "/delivery-rule/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/discount-rule/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/user/avatar", body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/user/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/cart/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/banner/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/file/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/note/" + id, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/role/" + role, body),
                new RouteCall(HttpMethod.PATCH, "/api/admin/role/" + role + "/users/" + id, body),

                // DELETE
                new RouteCall(HttpMethod.DELETE, "/api/admin/products/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product/" + id + "/paid-content/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product/" + id + "/variant/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-categories/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-category/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-tags/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-tag/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-attributes/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-attribute/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/product-attribute/" + id + "/option/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/order/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/payment/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/payment/" + id + "/refund", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/voucher-codes/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/voucher-code/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/delivery-types/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/delivery-type/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/delivery-type/" + id + "/delivery-rule/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/discount-rules/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/discount-rule/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/discount-rule/" + id + "/products/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/discount-rule/" + id + "/product-categories/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/user/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/cart/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/rating/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/banner/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/banner/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/files/bulk", body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/file/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/note/" + id, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/role/" + role, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/role/" + role + "/permission/" + permission, body),
                new RouteCall(HttpMethod.DELETE, "/api/admin/role/" + role + "/users/" + id, body)
        );
    }

    private ResponseEntity<Map> exchange(RouteCall call, HttpHeaders headers) {
        try {
            HttpEntity<?> entity = new HttpEntity<>(call.body, headers);
            return restTemplate.exchange(call.path, call.method, entity, Map.class);
        } catch (ResourceAccessException ex) {
            if (call.method == HttpMethod.PATCH && ex.getMessage() != null && ex.getMessage().contains("Invalid HTTP method: PATCH")) {
                return patchWithJdkClient(call.path, headers.getFirst(HttpHeaders.AUTHORIZATION), call.body);
            }
            throw ex;
        }
    }

    private ResponseEntity<Map> patchWithJdkClient(String path, String authorization, Object body) {
        try {
            String json = body == null ? "{}" : OBJECT_MAPPER.writeValueAsString(body);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create("http://localhost:" + port + path))
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(json))
                    .header("Content-Type", "application/json");
            if (authorization != null) {
                builder.header(HttpHeaders.AUTHORIZATION, authorization);
            }
            var response = HttpClient.newHttpClient().send(builder.build(), HttpResponse.BodyHandlers.ofString());
            Map responseBody = response.body() == null || response.body().isBlank()
                    ? null
                    : OBJECT_MAPPER.readValue(response.body(), Map.class);
            return ResponseEntity.status(response.statusCode()).body(responseBody);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private record RouteCall(HttpMethod method, String path, Object body) {}
}
