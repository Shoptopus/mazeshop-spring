package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrdersBulkOperationsTest extends AdminControllerTestBase {

    @Test
    void can_update_multiple_order_statuses() {
        String token = token();
        List<UUID> ids = List.of(seedOrder(1), seedOrder(1), seedOrder(1));

        var response = authed(token, HttpMethod.POST, "/api/admin/orders/bulk/status", Map.of("ids", ids, "status", 2));
        assert2xx(response);
        assertThat(jdbc.queryForObject("select status from orders where id = ?::uuid", Integer.class, ids.get(0).toString())).isEqualTo(2);
        assertThat(jdbc.queryForObject("select status from orders where id = ?::uuid", Integer.class, ids.get(1).toString())).isEqualTo(2);
    }

    @Test
    void bulk_order_status_validation_is_enforced() {
        String token = token();
        var missingIds = authed(token, HttpMethod.POST, "/api/admin/orders/bulk/status", Map.of("status", 2));
        assertValidationFailed(missingIds, "ids field is required");

        var invalidIds = authed(token, HttpMethod.POST, "/api/admin/orders/bulk/status", Map.of("ids", List.of("4321421"), "status", 2));
        assertValidationFailed(invalidIds, "selected ids.0 is invalid");
    }

    @Test
    void bulk_order_status_updates_require_super_admin_role() {
        String email = "orders-bulk-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Orders",
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

        List<UUID> ids = List.of(seedOrder(1));
        assertThat(authed(token, HttpMethod.POST, "/api/admin/orders/bulk/status", Map.of("ids", ids, "status", 2)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedOrder(int status) {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into users(id, first_name, last_name, email, password, name, slug, temporary, is_favorite, created_at, updated_at)
                values (?::uuid, 'Order', 'Tester', ?, '$2a$10$abcdefghijklmnopqrstuv', 'Order Tester', ?, false, false, ?, ?)
                """, userId.toString(), "order-bulk-" + orderId + "@example.com", "order-bulk-" + orderId, now, now);
        jdbc.update("""
                insert into addresses(id, address_line_1, town, post_code, slug, user_id, country, created_at, updated_at)
                values (?::uuid, 'Order St', 'Cardiff', 'CF10 1AA', ?, ?::uuid, 'UK', ?, ?)
                """, addressId.toString(), "address-" + addressId, userId.toString(), now, now);
        jdbc.update("""
                insert into orders(id, address_id, user_id, original_price, subtotal, total_price, total_discount, delivery_cost, currency_code, status, slug, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::uuid, 10, 9, 12, 1, 3, 'GBP', ?, ?, ?, ?)
                """, orderId.toString(), addressId.toString(), userId.toString(), status, "order-" + orderId, now, now);
        return orderId;
    }
}
