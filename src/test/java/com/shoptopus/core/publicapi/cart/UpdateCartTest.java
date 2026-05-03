package com.shoptopus.core.publicapi.cart;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateCartTest extends CartApiTestBase {

    @Test
    void unauthorised_users_are_not_allowed_to_update_carts() {
        var product = createProduct(10);
        var guestAdd = restTemplate.postForEntity("/api/cart/addItem", Map.of("product_id", product.getId(), "quantity", 1), Map.class);
        String guestCartId = String.valueOf(firstCart(guestAdd).get("id"));

        var unauthenticated = exchange(HttpMethod.PATCH, "/api/cart/" + guestCartId, null, Map.of("ip_address", "127.0.0.1"));
        assertThat(unauthenticated.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(unauthenticated.getBody().get("user_message")).isEqualTo("Unauthenticated");

        String ownerToken = registerAndLogin("update-owner-" + UUID.randomUUID() + "@example.com");
        var ownerAdd = exchange(HttpMethod.POST, "/api/cart/addItem", ownerToken, Map.of("product_id", product.getId(), "quantity", 1));
        String ownerCartId = String.valueOf(firstCart(ownerAdd).get("id"));

        String otherToken = registerAndLogin("update-other-" + UUID.randomUUID() + "@example.com");
        var unauthorized = exchange(HttpMethod.PATCH, "/api/cart/" + ownerCartId, otherToken, Map.of("ip_address", "127.0.0.1"));
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(unauthorized.getBody().get("developer_message")).isEqualTo("This action is unauthorized.");
    }

    @Test
    void authorised_users_can_update_carts() {
        String email = "update-cart-ok-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var product = createProduct(10);
        var add = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));

        var res = exchange(HttpMethod.PATCH, "/api/cart/" + cartId, token, Map.of("ip_address", "127.0.0.1"));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        String ipAddress = jdbc.queryForObject("select ip_address from carts where id = ?::uuid", String.class, cartId);
        assertThat(ipAddress).isNotBlank();
    }
}
