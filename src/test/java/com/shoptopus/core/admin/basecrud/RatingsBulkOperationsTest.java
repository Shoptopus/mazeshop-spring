package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RatingsBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_update_rating_verified_and_availability_in_bulk() {
        String token = token();
        List<UUID> ids = List.of(seedRating(false, false), seedRating(false, false), seedRating(false, false));

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/verified-status", Map.of("ids", ids, "verified", true)));
        assertThat(jdbc.queryForObject("select verified from ratings where id = ?::uuid", Boolean.class, ids.get(0).toString())).isTrue();

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/availability", Map.of("ids", ids, "availability", true)));
        assertThat(jdbc.queryForObject("select enabled from ratings where id = ?::uuid", Boolean.class, ids.get(1).toString())).isTrue();
    }

    @Test
    void bulk_rating_validation_is_enforced() {
        String token = token();
        List<UUID> ids = List.of(seedRating(false, false));

        assertValidationFailed(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/verified-status", Map.of("ids", ids)), "verified field is required");
        assertValidationFailed(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/availability", Map.of("ids", ids)), "availability field is required");
        assertValidationFailed(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/verified-status", Map.of("ids", List.of("invalid id"), "verified", true)), "selected ids.0 is invalid");
    }

    @Test
    void bulk_rating_operations_require_super_admin() {
        String token = customerToken("ratings");
        List<UUID> ids = List.of(seedRating(false, false));
        assertThat(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/verified-status", Map.of("ids", ids, "verified", true)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.POST, "/api/admin/ratings/bulk/availability", Map.of("ids", ids, "availability", true)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedRating(boolean enabled, boolean verified) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into ratings(id, rating, enabled, verified, slug, created_at, updated_at)
                values (?::uuid, 5, ?, ?, ?, now(), now())
                """,
                id, enabled, verified, "rating-" + id);
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
