package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeliveryTypesBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_enable_disable_and_delete_multiple_delivery_types() {
        String token = token();
        List<UUID> ids = List.of(seedDeliveryType(false), seedDeliveryType(false), seedDeliveryType(false));

        var enable = authed(token, HttpMethod.POST, "/api/admin/delivery-types/bulk/availability", Map.of("ids", ids, "availability", true));
        assert2xx(enable);
        assertThat(jdbc.queryForObject("select enabled from delivery_types where id = ?::uuid", Boolean.class, ids.get(0).toString())).isTrue();

        var disable = authed(token, HttpMethod.POST, "/api/admin/delivery-types/bulk/availability", Map.of("ids", ids, "availability", false));
        assert2xx(disable);
        assertThat(jdbc.queryForObject("select enabled from delivery_types where id = ?::uuid", Boolean.class, ids.get(1).toString())).isFalse();

        var delete = authed(token, HttpMethod.DELETE, "/api/admin/delivery-types/bulk", Map.of("ids", ids));
        assert2xx(delete);
        for (UUID id : ids) {
            Integer deleted = jdbc.queryForObject("select count(*) from delivery_types where id = ?::uuid and deleted_at is not null", Integer.class, id.toString());
            assertThat(deleted).isEqualTo(1);
        }
    }

    @Test
    void bulk_delivery_type_validation_is_enforced() {
        String token = token();
        var missingAvailability = authed(token, HttpMethod.POST, "/api/admin/delivery-types/bulk/availability", Map.of("ids", List.of(seedDeliveryType(true))));
        assertValidationFailed(missingAvailability, "availability field is required");

        var missingIds = authed(token, HttpMethod.DELETE, "/api/admin/delivery-types/bulk", Map.of());
        assertValidationFailed(missingIds, "ids field is required");

        var invalidIds = authed(token, HttpMethod.DELETE, "/api/admin/delivery-types/bulk", Map.of("ids", List.of("invalid id")));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");
    }

    @Test
    void bulk_delivery_type_operations_require_super_admin_role() {
        String email = "delivery-bulk-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Delivery",
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
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        List<UUID> ids = List.of(seedDeliveryType(true));
        assertThat(authed(token, HttpMethod.POST, "/api/admin/delivery-types/bulk/availability", Map.of("ids", ids, "availability", false)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(token, HttpMethod.DELETE, "/api/admin/delivery-types/bulk", Map.of("ids", ids)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedDeliveryType(boolean enabled) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into delivery_types(id, name, slug, description, price, enabled, enabled_by_default_on_creation, created_at, updated_at)
                values (?::uuid, '{"en":"Bulk"}'::jsonb, ?, '{"en":"Bulk"}'::jsonb, 0, ?, true, now(), now())
                """,
                id, "delivery-type-" + id, enabled);
        return UUID.fromString(id);
    }
}
