package com.shoptopus.core.publicapi.cart;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RemovesAllItemsFromCartTest extends CartApiTestBase {

    @Test
    void it_empties_cart_by_cart_id() {
        var product = createProduct(10);
        var add = restTemplate.postForEntity("/api/cart/addItem", Map.of("product_id", product.getId(), "quantity", 1), Map.class);
        String cartId = String.valueOf(firstCart(add).get("id"));

        Integer before = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid", Integer.class, cartId);
        assertThat(before).isEqualTo(1);

        var res = delete("/api/cart/all?cartId=" + cartId, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer after = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid", Integer.class, cartId);
        assertThat(after).isZero();
    }

    @Test
    void it_empties_cart_for_authenticated_user() {
        String email = "clear-owner-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var product = createProduct(10);
        var add = exchange(org.springframework.http.HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));

        var res = delete("/api/cart/all?cartId=" + cartId, token);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer after = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid", Integer.class, cartId);
        assertThat(after).isZero();
    }

    @Test
    void it_fails_if_cart_id_does_not_match_authenticated_users_cart() {
        String ownerEmail = "clear-owner2-" + UUID.randomUUID() + "@example.com";
        String ownerToken = registerAndLogin(ownerEmail);
        String otherToken = registerAndLogin("clear-other-" + UUID.randomUUID() + "@example.com");

        var product = createProduct(10);
        var add = exchange(org.springframework.http.HttpMethod.POST, "/api/cart/addItem", ownerToken, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));

        var res = delete("/api/cart/all?cartId=" + cartId, otherToken);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(res.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    @Test
    void it_fails_if_cart_id_is_invalid_or_missing() {
        var invalid = delete("/api/cart/all?cartId=INVALID", null);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var missing = delete("/api/cart/all", null);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(missing.getBody().get("developer_message"))).contains("Required request parameter");
    }
}
