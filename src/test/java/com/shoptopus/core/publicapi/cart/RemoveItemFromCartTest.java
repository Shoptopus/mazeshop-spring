package com.shoptopus.core.publicapi.cart;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RemoveItemFromCartTest extends CartApiTestBase {

    @Test
    void it_has_all_required_fields() {
        var res = delete("/api/cart/removeItem", null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("developer_message"))).contains("Required request parameter");
    }

    @Test
    void the_product_must_exist() {
        var product = createProduct(10);
        var add = restTemplate.postForEntity("/api/cart/addItem", Map.of("product_id", product.getId(), "quantity", 1), Map.class);
        String cartId = String.valueOf(firstCart(add).get("id"));

        var res = delete("/api/cart/removeItem?cartId=" + cartId + "&productId=" + UUID.randomUUID(), null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Invalid product id");
    }

    @Test
    void if_authenticated_the_cart_must_belong_to_the_user() {
        String ownerEmail = "remove-owner-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        var product = createProduct(10);
        var add = exchange(org.springframework.http.HttpMethod.POST, "/api/cart/addItem", ownerToken, Map.of(
                "product_id", product.getId(),
                "quantity", 1
        ));
        String cartId = String.valueOf(firstCart(add).get("id"));

        String otherToken = registerAndLogin("remove-other-" + UUID.randomUUID() + "@example.com");
        var res = delete("/api/cart/removeItem?cartId=" + cartId + "&productId=" + product.getId(), otherToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    @Test
    void it_removes_the_product_from_the_cart() {
        var product = createProduct(10);
        var add = restTemplate.postForEntity("/api/cart/addItem", Map.of("product_id", product.getId(), "quantity", 1), Map.class);
        String cartId = String.valueOf(firstCart(add).get("id"));

        Integer before = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid", Integer.class, cartId, product.getId().toString());
        assertThat(before).isEqualTo(1);

        var res = delete("/api/cart/removeItem?cartId=" + cartId + "&productId=" + product.getId(), null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer after = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid", Integer.class, cartId, product.getId().toString());
        assertThat(after).isZero();
    }

    @Test
    void it_updates_last_seen_for_authenticated_user() {
        String email = "remove-lastseen-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = userIdByEmail(email);
        var product = createProduct(10);

        var add = exchange(org.springframework.http.HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));
        jdbc.update("update users set last_seen = null where id = ?::uuid", userId.toString());

        var res = delete("/api/cart/removeItem?cartId=" + cartId + "&productId=" + product.getId(), token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        OffsetDateTime lastSeen = jdbc.queryForObject("select last_seen from users where id = ?::uuid", OffsetDateTime.class, userId.toString());
        assertThat(lastSeen).isNotNull();
    }

    @Test
    void it_removes_the_appropriate_product_variant_only() {
        var product = createProduct(20);
        var variant1 = createVariant(product, 25);
        var variant2 = createVariant(product, 25);

        var first = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", product.getId(),
                "product_variant_id", variant1.getId(),
                "quantity", 1
        ), Map.class);
        String cartId = String.valueOf(firstCart(first).get("id"));

        restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "cart_id", cartId,
                "product_id", product.getId(),
                "product_variant_id", variant2.getId(),
                "quantity", 1
        ), Map.class);

        var res = delete("/api/cart/removeItem?cartId=" + cartId + "&productId=" + product.getId() + "&productVariantId=" + variant1.getId(), null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer missing = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid",
                Integer.class, cartId, product.getId().toString(), variant1.getId().toString());
        Integer exists = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid",
                Integer.class, cartId, product.getId().toString(), variant2.getId().toString());
        assertThat(missing).isZero();
        assertThat(exists).isEqualTo(1);
    }
}
