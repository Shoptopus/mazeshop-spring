package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductsBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void products_can_be_bulk_archived_and_deleted() {
        String token = token();
        List<UUID> ids = List.of(seedProduct(), seedProduct());

        var archive = authed(token, HttpMethod.POST, "/api/admin/products/bulk/archive", Map.of("ids", ids));
        assert2xx(archive);
        assertThat(jdbc.queryForObject("select status from products where id = ?::uuid", Integer.class, ids.get(0).toString())).isEqualTo(2);

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/products/bulk", Map.of("ids", ids));
        assert2xx(delete);
        assertThat(jdbc.queryForObject("select count(*) from products where id = ?::uuid and deleted_at is not null", Integer.class, ids.get(1).toString())).isEqualTo(1);
    }

    @Test
    void product_bulk_validation_and_permissions_are_enforced() {
        String token = token();
        var missingIds = authed(token, HttpMethod.POST, "/api/admin/products/bulk/archive", Map.of());
        assertValidationFailed(missingIds, "ids field is required");

        var invalidIds = authed(token, HttpMethod.DELETE, "/api/admin/products/bulk", Map.of("ids", List.of("bad-id")));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");

        String email = "products-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Products",
                "last_name", "Blocked",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "customer");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String blockedToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(blockedToken, HttpMethod.POST, "/api/admin/products/bulk/archive", Map.of("ids", List.of(seedProduct()))).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedProduct() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, stock, virtual, status, purchase_count, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, 10, 4, false, 0, 0, now(), now())
                """,
                id, "{\"en\":\"Bulk product\"}", "bulk-product-" + id, "{\"en\":\"Short\"}", "{\"en\":\"Description\"}");
        return UUID.fromString(id);
    }
}
