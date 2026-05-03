package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class BannersBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_update_banner_availability_and_delete_in_bulk() {
        String token = token();
        List<UUID> ids = List.of(seedBanner(false), seedBanner(false), seedBanner(false));

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/banners/bulk/availability", Map.of("ids", ids, "availability", true)));
        assertThat(jdbc.queryForObject("select enabled from banners where id = ?::uuid", Boolean.class, ids.get(0).toString())).isTrue();

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/banners/bulk", Map.of("ids", ids)));
        assertThat(jdbc.queryForObject("select count(*) from banners where id = ?::uuid and deleted_at is not null", Integer.class, ids.get(1).toString())).isEqualTo(1);
    }

    @Test
    void bulk_banner_validation_is_enforced() {
        String token = token();
        List<UUID> ids = List.of(seedBanner(false));

        assertValidationFailed(authed(token, HttpMethod.POST, "/api/admin/banners/bulk/availability", Map.of("ids", ids)), "availability field is required");
        assertValidationFailed(authed(token, HttpMethod.DELETE, "/api/admin/banners/bulk", Map.of()), "ids field is required");
        assertValidationFailed(authed(token, HttpMethod.DELETE, "/api/admin/banners/bulk", Map.of("ids", List.of("invalid id"))), "selected ids.0 is invalid");
    }

    @Test
    void bulk_banner_operations_require_super_admin() {
        String token = customerToken("banners");
        List<UUID> ids = List.of(seedBanner(false));
        assertThat(authed(token, HttpMethod.POST, "/api/admin/banners/bulk/availability", Map.of("ids", ids, "availability", true)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.DELETE, "/api/admin/banners/bulk", Map.of("ids", ids)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedBanner(boolean enabled) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into banners(id, name, slug, image, click_count, enabled, created_at, updated_at)
                values (?::uuid, '{"en":"Banner"}'::jsonb, ?, '{}'::jsonb, 0, ?, now(), now())
                """,
                id, "banner-" + id, enabled);
        return UUID.fromString(id);
    }

    private String customerToken(String prefix) {
        String email = prefix + "-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", prefix,
                "last_name", "Customer",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "customer");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }
}
