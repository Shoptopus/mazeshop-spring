package com.shoptopus.core.publicapi.auth;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class LoginWithCartIdTest extends AuthApiTestBase {

    @Test
    void existing_user_cart_is_merged_with_guest_cart() {
        String email = "merge-carts-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Merge",
                "last_name", "User",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);

        UUID userId = userIdByEmail(email);
        UUID userCartId = findOrCreateUserCart(userId);
        UUID guestCartId = createGuestCart();

        List<UUID> products = ensureProducts(2);
        UUID productFront = products.get(0);
        UUID productBack = products.get(1);

        insertCartProduct(guestCartId, productFront, 1);
        insertCartProduct(userCartId, productBack, 1);

        var res = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123",
                "cart_id", guestCartId.toString()
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        var auth = (Map<String, Object>) ((Map<String, Object>) res.getBody().get("data")).get("auth");
        @SuppressWarnings("unchecked")
        var user = (Map<String, Object>) auth.get("user");
        @SuppressWarnings("unchecked")
        var cart = (Map<String, Object>) user.get("cart");
        @SuppressWarnings("unchecked")
        var cartProducts = (List<Map<String, Object>>) cart.get("products");
        assertThat(cartProducts).hasSize(2);
    }

    @Test
    void merge_sums_quantity_for_same_product() {
        String email = "merge-qty-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Merge",
                "last_name", "Qty",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);

        UUID userId = userIdByEmail(email);
        UUID userCartId = findOrCreateUserCart(userId);
        UUID guestCartId = createGuestCart();

        UUID productId = ensureProducts(1).get(0);
        insertCartProduct(guestCartId, productId, 1);
        insertCartProduct(userCartId, productId, 1);

        var res = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123",
                "cart_id", guestCartId.toString()
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        var auth = (Map<String, Object>) ((Map<String, Object>) res.getBody().get("data")).get("auth");
        @SuppressWarnings("unchecked")
        var user = (Map<String, Object>) auth.get("user");
        @SuppressWarnings("unchecked")
        var cart = (Map<String, Object>) user.get("cart");
        @SuppressWarnings("unchecked")
        var cartProducts = (List<Map<String, Object>>) cart.get("products");
        assertThat(cartProducts).hasSize(1);
        assertThat(String.valueOf(cartProducts.get(0).get("quantity"))).isEqualTo("2");
    }

    @Test
    void old_guest_cart_is_deleted_after_merge() {
        String email = "merge-delete-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Merge",
                "last_name", "Delete",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);

        UUID userId = userIdByEmail(email);
        UUID userCartId = findOrCreateUserCart(userId);
        UUID guestCartId = createGuestCart();

        List<UUID> products = ensureProducts(2);
        UUID productFront = products.get(0);
        UUID productBack = products.get(1);

        insertCartProduct(guestCartId, productFront, 1);
        insertCartProduct(userCartId, productBack, 1);

        Integer beforeCart = jdbc.queryForObject("select count(*) from carts where id = ?::uuid", Integer.class, guestCartId.toString());
        assertThat(beforeCart).isEqualTo(1);

        var res = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123",
                "cart_id", guestCartId.toString()
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer cartRows = jdbc.queryForObject("select count(*) from carts where id = ?::uuid", Integer.class, guestCartId.toString());
        Integer itemRows = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid", Integer.class, guestCartId.toString());
        assertThat(cartRows).isEqualTo(0);
        assertThat(itemRows).isEqualTo(0);
    }

    private UUID createGuestCart() {
        UUID cartId = UUID.randomUUID();
        jdbc.update(
                "insert into carts(id, user_id, ip_address, slug, created_at, updated_at) values (?::uuid, null, ?, ?, now(), now())",
                cartId.toString(),
                "127.0.0.1",
                "guest-cart-" + cartId
        );
        return cartId;
    }

    private UUID findOrCreateUserCart(UUID userId) {
        List<String> rows = jdbc.queryForList(
                "select id::text from carts where user_id = ?::uuid order by created_at desc limit 1",
                String.class,
                userId.toString()
        );
        if (!rows.isEmpty()) {
            return UUID.fromString(rows.get(0));
        }

        UUID cartId = UUID.randomUUID();
        jdbc.update(
                "insert into carts(id, user_id, ip_address, slug, created_at, updated_at) values (?::uuid, ?::uuid, ?, ?, now(), now())",
                cartId.toString(),
                userId.toString(),
                "127.0.0.1",
                "user-cart-" + cartId
        );
        return cartId;
    }

    private List<UUID> ensureProducts(int count) {
        List<String> existing = jdbc.queryForList("select id::text from products order by created_at asc limit " + count, String.class);
        java.util.ArrayList<UUID> ids = new java.util.ArrayList<>(existing.stream().map(UUID::fromString).toList());
        while (ids.size() < count) {
            UUID id = UUID.randomUUID();
            String slug = "auto-product-" + id;
            jdbc.update(
                    "insert into products(id, name, slug, short_description, description, price, status, purchase_count, stock, virtual, created_at, updated_at) " +
                            "values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, 0, 0, 100, false, now(), now())",
                    id.toString(),
                    "{\"en\":\"Auto Product\"}",
                    slug,
                    "{\"en\":\"Short\"}",
                    "{\"en\":\"Description\"}",
                    9.99
            );
            ids.add(id);
        }
        return ids;
    }

    private void insertCartProduct(UUID cartId, UUID productId, long quantity) {
        jdbc.update(
                "insert into cart_product(id, cart_id, product_id, product_variant_id, quantity, created_at, updated_at) values (?::uuid, ?::uuid, ?::uuid, null, ?, now(), now())",
                UUID.randomUUID().toString(),
                cartId.toString(),
                productId.toString(),
                quantity
        );
    }
}
