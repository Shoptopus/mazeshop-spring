package com.shoptopus.core.publicapi.cart;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AddItemToCartTest extends CartApiTestBase {

    @Test
    void it_has_all_required_fields() {
        var res = restTemplate.postForEntity("/api/cart/addItem", Map.of(), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(res.getBody().get("errors"))).contains("productId", "quantity");
    }

    @Test
    void the_product_must_exist_and_quantity_must_be_at_least_one() {
        var invalidProduct = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", UUID.randomUUID(),
                "quantity", 2
        ), Map.class);
        assertThat(invalidProduct.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidProduct.getBody().get("developer_message")).isEqualTo("Invalid product id");

        var product = createProduct(10);
        var invalidQuantity = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", product.getId(),
                "quantity", 0
        ), Map.class);
        assertThat(invalidQuantity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(invalidQuantity.getBody().get("errors"))).contains("quantity");
    }

    @Test
    void stock_validation_messages_are_returned() {
        var outOfStock = createProduct(0);
        var out = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", outOfStock.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(out.getBody().get("developer_message")).isEqualTo("Out of stock.");

        var lowStock = createProduct(1);
        var unavailable = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", lowStock.getId(),
                "quantity", 2
        ), Map.class);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unavailable.getBody().get("developer_message")).isEqualTo("Only 1 left.");
    }

    @Test
    void if_cart_id_is_present_it_must_exist() {
        var product = createProduct(10);
        var res = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "cart_id", UUID.randomUUID(),
                "product_id", product.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Invalid cart id");
    }

    @Test
    void it_creates_new_cart_and_persists_cart_product_for_unauthenticated_user() {
        var product = createProduct(10);
        var res = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", product.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var cart = firstCart(res);
        String cartId = String.valueOf(cart.get("id"));
        Integer carts = jdbc.queryForObject("select count(*) from carts where id = ?::uuid and user_id is null", Integer.class, cartId);
        Integer rows = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and quantity = 1", Integer.class, cartId, product.getId().toString());
        assertThat(carts).isEqualTo(1);
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void existing_records_get_correctly_updated() {
        var product = createProduct(10);
        var first = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", product.getId(),
                "quantity", 1
        ), Map.class);
        String cartId = String.valueOf(firstCart(first).get("id"));

        var second = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "cart_id", cartId,
                "product_id", product.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer rows = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and quantity = 2", Integer.class, cartId, product.getId().toString());
        assertThat(rows).isEqualTo(1);
    }

    @Test
    void it_returns_all_required_data() {
        var product = createProduct(10);
        var res = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", product.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var cart = firstCart(res);
        assertThat(cart.keySet().stream().map(String::valueOf).toList()).contains("id", "user_id", "ip_address", "user", "products");

        @SuppressWarnings("unchecked")
        var products = (List<Map<String, Object>>) cart.get("products");
        assertThat(products).isNotEmpty();
        var item = products.get(0);
        assertThat(item.keySet().stream().map(String::valueOf).toList())
                .contains("id", "product_id", "product_variant_id", "name", "item_original_price", "item_final_price",
                        "subtotal_original_price", "subtotal_final_price", "quantity", "remaining_stock", "in_other_carts");
    }

    @Test
    void it_updates_last_seen_for_authenticated_user() {
        String email = "cart-last-seen-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        UUID userId = userIdByEmail(email);
        var product = createProduct(10);

        jdbc.update("update users set last_seen = null where id = ?::uuid", userId.toString());
        var res = exchange(org.springframework.http.HttpMethod.POST, "/api/cart/addItem", token, Map.of(
                "product_id", product.getId(),
                "quantity", 1
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        OffsetDateTime lastSeen = jdbc.queryForObject("select last_seen from users where id = ?::uuid", OffsetDateTime.class, userId.toString());
        assertThat(lastSeen).isNotNull();
    }

    @Test
    void it_adds_different_product_variants_correctly() {
        var product = createProduct(20);
        var variant1 = createVariant(product, 25);
        var variant2 = createVariant(product, 25);

        var first = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", product.getId(),
                "product_variant_id", variant1.getId(),
                "quantity", 1
        ), Map.class);
        String cartId = String.valueOf(firstCart(first).get("id"));

        var second = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "cart_id", cartId,
                "product_id", product.getId(),
                "product_variant_id", variant2.getId(),
                "quantity", 2
        ), Map.class);
        assertThat(second.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer v1 = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid and quantity = 1",
                Integer.class, cartId, product.getId().toString(), variant1.getId().toString());
        Integer v2 = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid and quantity = 2",
                Integer.class, cartId, product.getId().toString(), variant2.getId().toString());
        assertThat(v1).isEqualTo(1);
        assertThat(v2).isEqualTo(1);
    }

    @Test
    void appropriate_variant_gets_correctly_updated() {
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

        restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "cart_id", cartId,
                "product_id", product.getId(),
                "product_variant_id", variant1.getId(),
                "quantity", 2
        ), Map.class);

        Integer updated = jdbc.queryForObject(
                "select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid and quantity = 3",
                Integer.class, cartId, product.getId().toString(), variant1.getId().toString());
        assertThat(updated).isEqualTo(1);
    }
}
