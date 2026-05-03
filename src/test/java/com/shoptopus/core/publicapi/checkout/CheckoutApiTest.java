package com.shoptopus.core.publicapi.checkout;

import com.shoptopus.core.IntegrationTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CheckoutApiTest extends IntegrationTestBase {

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void check_product_availabilities_returns_unavailable_for_empty_cart() {
        UUID cartId = createCart(null);
        var res = restTemplate.getForEntity("/api/checkout/check-availabilities/" + cartId, Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.get("available")).isEqualTo(false);
    }

    @Test
    void guest_checkout_attempting_to_checkout_empty_cart_fails_gracefully() {
        UUID cartId = createCart(null);
        var res = restTemplate.postForEntity("/api/checkout/create-pending-order", Map.of(
                "cart_id", cartId.toString(),
                "user", Map.of("email", "guest-empty-" + UUID.randomUUID() + "@example.com"),
                "address", Map.of("address_line_1", "1 Test St", "town", "London", "post_code", "E1 1AA")
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Cannot checkout an empty cart.");
    }

    @Test
    void guest_checkout_create_pending_order_persists_order() {
        UUID productId = ensureProduct();
        UUID cartId = createCartWithItem(null, productId, 2);
        String guestEmail = "guest-checkout-" + UUID.randomUUID() + "@example.com";

        var res = restTemplate.postForEntity("/api/checkout/create-pending-order", Map.of(
                "cart_id", cartId.toString(),
                "user", Map.of(
                        "email", guestEmail,
                        "first_name", "Guest",
                        "last_name", "Checkout"
                ),
                "address", Map.of(
                        "address_line_1", "1 Test St",
                        "town", "London",
                        "post_code", "E1 1AA",
                        "lat", "51.5",
                        "lon", "-0.12"
                )
        ), Map.class);

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        @SuppressWarnings("unchecked")
        var data = (Map<String, Object>) res.getBody().get("data");
        UUID orderId = UUID.fromString(String.valueOf(data.get("order_id")));

        Integer orderRows = jdbc.queryForObject("select count(*) from orders where id = ?::uuid", Integer.class, orderId.toString());
        assertThat(orderRows).isEqualTo(1);
    }

    @Test
    void guest_checkout_revert_pending_order_updates_order_status_to_cancelled() {
        UUID productId = ensureProduct();
        UUID cartId = createCartWithItem(null, productId, 1);
        String guestEmail = "guest-revert-" + UUID.randomUUID() + "@example.com";

        var create = restTemplate.postForEntity("/api/checkout/create-pending-order", Map.of(
                "cart_id", cartId.toString(),
                "user", Map.of("email", guestEmail),
                "address", Map.of("address_line_1", "1 Test St", "town", "London", "post_code", "E1 1AA")
        ), Map.class);
        @SuppressWarnings("unchecked")
        UUID orderId = UUID.fromString(String.valueOf(((Map<String, Object>) create.getBody().get("data")).get("order_id")));

        var revert = restTemplate.postForEntity("/api/checkout/revert-order", Map.of("order_id", orderId.toString()), Map.class);
        assertThat(revert.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer status = jdbc.queryForObject("select status from orders where id = ?::uuid", Integer.class, orderId.toString());
        assertThat(status).isEqualTo(6);
    }

    @Test
    void apply_voucher_code_requires_code() {
        var res = restTemplate.postForEntity("/api/checkout/apply-voucher-code", Map.of(), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private UUID ensureProduct() {
        String existing = jdbc.query("select id::text from products where deleted_at is null order by created_at asc limit 1", rs -> rs.next() ? rs.getString(1) : null);
        if (existing != null) {
            return UUID.fromString(existing);
        }
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into products(id, name, slug, short_description, description, price, status, purchase_count, stock, virtual, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, 0, 0, 100, false, now(), now())",
                id.toString(),
                "{\"en\":\"Checkout Product\"}",
                "checkout-product-" + id,
                "{\"en\":\"Short\"}",
                "{\"en\":\"Description\"}",
                14.99
        );
        return id;
    }

    private UUID createCart(UUID userId) {
        UUID cartId = UUID.randomUUID();
        jdbc.update(
                "insert into carts(id, user_id, ip_address, slug, created_at, updated_at) values (?::uuid, ?::uuid, ?, ?, now(), now())",
                cartId.toString(),
                userId == null ? null : userId.toString(),
                "127.0.0.1",
                "checkout-cart-" + cartId
        );
        return cartId;
    }

    private UUID createCartWithItem(UUID userId, UUID productId, long quantity) {
        UUID cartId = createCart(userId);
        jdbc.update(
                "insert into cart_product(id, cart_id, product_id, product_variant_id, quantity, created_at, updated_at) values (?::uuid, ?::uuid, ?::uuid, null, ?, now(), now())",
                UUID.randomUUID().toString(),
                cartId.toString(),
                productId.toString(),
                quantity
        );
        return cartId;
    }
}
