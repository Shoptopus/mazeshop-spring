package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class OrderControllerTest extends AdminControllerTestBase {

    @Test
    void orders_can_be_listed_and_shown() {
        String token = token();
        UUID deliveryTypeId = seedDeliveryType(Map.of("en", "Tracked", "de", "Verfolgt"), Map.of("en", "Tracked post", "de", "Verfolgte post"));
        UUID orderId = seedOrder(deliveryTypeId, 1);
        seedPayment(orderId);

        var list = authed(token, HttpMethod.GET, "/api/admin/orders", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> orderId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("status")).isEqualTo(1);
        assertThat(row.get("delivery_type")).isEqualTo(Map.of("en", "Tracked", "de", "Verfolgt"));

        var show = authed(token, HttpMethod.GET, "/api/admin/order/" + orderId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(orderId.toString());
        assertThat(((Map<?, ?>) data.get("delivery_type")).get("name")).isEqualTo(Map.of("en", "Tracked", "de", "Verfolgt"));
        assertThat(data.get("payments")).isInstanceOf(List.class);
        assertThat(data.get("address")).isInstanceOf(Map.class);
        assertThat(data.get("user")).isInstanceOf(Map.class);
    }

    @Test
    void order_delivery_type_falls_back_to_invoice_snapshot_after_delivery_type_delete() {
        String token = token();
        UUID deliveryTypeId = seedDeliveryType(Map.of("en", "Courier", "de", "Kurier"), Map.of("en", "Courier service", "de", "Kurierdienst"));
        UUID orderId = seedOrder(deliveryTypeId, 0);
        seedInvoice(orderId, Map.of(
                "name", Map.of("en", "Courier", "de", "Kurier"),
                "description", Map.of("en", "Courier service", "de", "Kurierdienst")
        ));
        jdbc.update("update delivery_types set deleted_at = now() where id = ?::uuid", deliveryTypeId.toString());

        var list = authed(token, HttpMethod.GET, "/api/admin/orders", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> orderId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("delivery_type")).isEqualTo(Map.of("en", "Courier", "de", "Kurier"));

        var show = authed(token, HttpMethod.GET, "/api/admin/order/" + orderId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(((Map<?, ?>) data.get("delivery_type")).get("name")).isEqualTo(Map.of("en", "Courier", "de", "Kurier"));
        assertThat(((Map<?, ?>) data.get("delivery_type")).get("description")).isEqualTo(Map.of("en", "Courier service", "de", "Kurierdienst"));
    }

    @Test
    void order_status_can_be_updated_and_deleted() {
        String token = token();
        UUID orderId = seedOrder(null, 0);

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/order/" + orderId, Map.of("status", 3)));
        assertThat(jdbc.queryForObject("select status from orders where id = ?::uuid", Integer.class, orderId.toString())).isEqualTo(3);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/order/" + orderId, null));
        assertThat(jdbc.queryForObject("select count(*) from orders where id = ?::uuid and deleted_at is not null", Integer.class, orderId.toString())).isEqualTo(1);
    }

    @Test
    void order_validation_and_authorization_are_enforced() {
        String token = token();
        UUID orderId = seedOrder(null, 0);

        var missingStatus = authed(token, HttpMethod.PATCH, "/api/admin/order/" + orderId, Map.of());
        assertValidationFailed(missingStatus, "status field is required");

        var invalidStatus = authed(token, HttpMethod.POST, "/api/admin/orders/bulk/status", Map.of("ids", List.of(orderId), "status", "active"));
        assertValidationFailed(invalidStatus, "status field is invalid");

        String email = "order-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Order",
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
        String customerToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(customerToken, HttpMethod.GET, "/api/admin/orders", null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(customerToken, HttpMethod.PATCH, "/api/admin/order/" + orderId, Map.of("status", 2)).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(authed(customerToken, HttpMethod.DELETE, "/api/admin/order/" + orderId, null).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private UUID seedDeliveryType(Map<String, String> name, Map<String, String> description) {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into delivery_types(id, name, slug, description, price, enabled, enabled_by_default_on_creation, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, 5, true, true, now(), now())
                """,
                id, json(name), "delivery-type-" + id, json(description));
        return UUID.fromString(id);
    }

    private UUID seedOrder(UUID deliveryTypeId, int status) {
        UUID userId = UUID.randomUUID();
        UUID addressId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        jdbc.update("""
                insert into users(id, first_name, last_name, email, password, name, slug, temporary, is_favorite, created_at, updated_at)
                values (?::uuid, 'Order', 'Tester', ?, '$2a$10$abcdefghijklmnopqrstuv', 'Order Tester', ?, false, false, ?, ?)
                """, userId.toString(), "order-" + orderId + "@example.com", "order-" + orderId, now, now);
        jdbc.update("""
                insert into addresses(id, address_line_1, town, post_code, slug, user_id, country, created_at, updated_at)
                values (?::uuid, 'Order St', 'Cardiff', 'CF10 1AA', ?, ?::uuid, 'UK', ?, ?)
                """, addressId.toString(), "address-" + addressId, userId.toString(), now, now);
        jdbc.update("""
                insert into orders(id, address_id, user_id, delivery_type_id, original_price, subtotal, total_price, total_discount, delivery_cost, currency_code, status, slug, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 10, 9, 12, 1, 3, 'GBP', ?, ?, ?, ?)
                """, orderId.toString(), addressId.toString(), userId.toString(), deliveryTypeId == null ? null : deliveryTypeId.toString(), status, "order-" + orderId, now, now);
        return orderId;
    }

    private void seedPayment(UUID orderId) {
        String paymentId = UUID.randomUUID().toString();
        jdbc.update("""
                insert into payments(id, payable_type, payable_id, amount, slug, status, type, created_at, updated_at)
                values (?::uuid, 'App\\Models\\Order', ?, 12, ?, 1, 0, now(), now())
                """,
                paymentId, orderId.toString(), "payment-" + paymentId);
    }

    private void seedInvoice(UUID orderId, Map<String, Object> deliveryType) {
        String invoiceId = UUID.randomUUID().toString();
        String userId = jdbc.queryForObject("select user_id::text from orders where id = ?::uuid", String.class, orderId.toString());
        jdbc.update("""
                insert into invoices(id, user_id, order_id, address, payment, products, voucher_code, delivery_type, totals, valid, slug, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::uuid, '{}'::jsonb, '{}'::jsonb, '{}'::jsonb, null, ?::jsonb, '{}'::jsonb, true, ?, now(), now())
                """,
                invoiceId, userId, orderId.toString(), jsonObject(deliveryType), "invoice-" + invoiceId);
    }

    private String json(Map<String, ?> value) {
        return value.entrySet().stream()
                .map(entry -> "\"" + entry.getKey() + "\":" + jsonValue(entry.getValue()))
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    private String jsonObject(Map<String, Object> value) {
        return json(value);
    }

    private String jsonValue(Object value) {
        if (value instanceof Map<?, ?> map) {
            return json((Map<String, ?>) map);
        }
        return "\"" + String.valueOf(value) + "\"";
    }
}
