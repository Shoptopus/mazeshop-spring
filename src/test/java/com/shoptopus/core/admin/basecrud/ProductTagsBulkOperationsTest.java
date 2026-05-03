package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductTagsBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void product_tags_can_be_bulk_updated_and_deleted() {
        String token = token();
        List<UUID> ids = List.of(seedTag(), seedTag());

        var availability = authed(token, HttpMethod.POST, "/api/admin/product-tags/bulk/availability", Map.of(
                "ids", ids,
                "enabled", false
        ));
        assert2xx(availability);
        assertThat(jdbc.queryForObject("select enabled from product_tags where id = ?::uuid", Boolean.class, ids.get(0).toString())).isFalse();

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/product-tags/bulk", Map.of("ids", ids));
        assert2xx(delete);
        assertThat(jdbc.queryForObject("select count(*) from product_tags where id = ?::uuid and deleted_at is not null", Integer.class, ids.get(1).toString())).isEqualTo(1);
    }

    @Test
    void product_tag_bulk_validation_and_permissions_are_enforced() {
        String token = token();
        var missingIds = authed(token, HttpMethod.POST, "/api/admin/product-tags/bulk/availability", Map.of("enabled", false));
        assertValidationFailed(missingIds, "ids field is required");

        var invalidIds = authed(token, HttpMethod.DELETE, "/api/admin/product-tags/bulk", Map.of("ids", List.of("bad-id")));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");

        String email = "tag-bulk-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Tag",
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
        assertThat(authed(blockedToken, HttpMethod.POST, "/api/admin/product-tags/bulk/availability", Map.of(
                "ids", List.of(seedTag()),
                "enabled", false
        )).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedTag() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into product_tags(id, name, slug, description, enabled, display_badge, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, true, false, now(), now())
                """,
                id, "{\"en\":\"Bulk tag\"}", "bulk-tag-" + id, "{\"en\":\"Bulk tag description\"}");
        return UUID.fromString(id);
    }
}
