package com.shoptopus.core.publicapi.cart;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateQuantityTest extends CartApiTestBase {

    @Test
    void it_requires_a_valid_cart_id_and_valid_product_id() {
        var invalidCartId = exchange(HttpMethod.PATCH, "/api/cart/1234/product/quantity", null, Map.of(
                "product_id", UUID.randomUUID(),
                "quantity", 1
        ));
        assertThat(invalidCartId.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String email = "qty-invalid-product-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var product = createProduct(10);
        var add = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));

        var invalidProduct = exchange(HttpMethod.PATCH, "/api/cart/" + cartId + "/product/quantity", token, Map.of(
                "product_id", UUID.randomUUID(),
                "quantity", 1
        ));
        assertThat(invalidProduct.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidProduct.getBody().get("developer_message")).isEqualTo("Invalid product id");
    }

    @Test
    void it_validates_quantity_and_stock_errors() {
        String email = "qty-stock-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var minQuantityProduct = createProduct(10);
        var addMin = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", minQuantityProduct.getId(), "quantity", 1));
        String minCartId = String.valueOf(firstCart(addMin).get("id"));
        var minQuantity = exchange(HttpMethod.PATCH, "/api/cart/" + minCartId + "/product/quantity", token, Map.of(
                "product_id", minQuantityProduct.getId(),
                "quantity", 0
        ));
        assertThat(minQuantity.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(String.valueOf(minQuantity.getBody().get("errors"))).contains("quantity");

        var outOfStockProduct = createProduct(0);
        jdbc.update("insert into cart_product(id, cart_id, product_id, product_variant_id, quantity, created_at, updated_at) values (?::uuid, ?::uuid, ?::uuid, null, 1, now(), now())",
                UUID.randomUUID().toString(), minCartId, outOfStockProduct.getId().toString());
        var outOfStock = exchange(HttpMethod.PATCH, "/api/cart/" + minCartId + "/product/quantity", token, Map.of(
                "product_id", outOfStockProduct.getId(),
                "quantity", 1
        ));
        assertThat(outOfStock.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(outOfStock.getBody().get("developer_message")).isEqualTo("Out of stock.");

        var lowStockProduct = createProduct(1);
        jdbc.update("insert into cart_product(id, cart_id, product_id, product_variant_id, quantity, created_at, updated_at) values (?::uuid, ?::uuid, ?::uuid, null, 1, now(), now())",
                UUID.randomUUID().toString(), minCartId, lowStockProduct.getId().toString());
        var unavailable = exchange(HttpMethod.PATCH, "/api/cart/" + minCartId + "/product/quantity", token, Map.of(
                "product_id", lowStockProduct.getId(),
                "quantity", 2
        ));
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unavailable.getBody().get("developer_message")).isEqualTo("Only 1 left.");
    }

    @Test
    void the_product_must_exist_in_the_cart() {
        String email = "qty-not-in-cart-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var product = createProduct(10);
        var add = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));
        var anotherProduct = createProduct(10);

        var res = exchange(HttpMethod.PATCH, "/api/cart/" + cartId + "/product/quantity", token, Map.of(
                "product_id", anotherProduct.getId(),
                "quantity", 3
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(res.getBody().get("developer_message")).isEqualTo("Cart item not found");
    }

    @Test
    void quantity_updates_for_product_and_product_variant() {
        String email = "qty-update-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);

        var product = createProduct(10);
        var add = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));

        var updateProduct = exchange(HttpMethod.PATCH, "/api/cart/" + cartId + "/product/quantity", token, Map.of(
                "product_id", product.getId(),
                "quantity", 4
        ));
        assertThat(updateProduct.getStatusCode()).isEqualTo(HttpStatus.OK);
        Integer productRow = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id is null and quantity = 4",
                Integer.class, cartId, product.getId().toString());
        assertThat(productRow).isEqualTo(1);

        var variantProduct = createProduct(20);
        var variant1 = createVariant(variantProduct, 25);
        var variant2 = createVariant(variantProduct, 25);
        var addVariant1 = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of(
                "cart_id", cartId,
                "product_id", variantProduct.getId(),
                "product_variant_id", variant1.getId(),
                "quantity", 1
        ));
        assertThat(addVariant1.getStatusCode()).isEqualTo(HttpStatus.OK);
        exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of(
                "cart_id", cartId,
                "product_id", variantProduct.getId(),
                "product_variant_id", variant2.getId(),
                "quantity", 1
        ));

        var updateVariant = exchange(HttpMethod.PATCH, "/api/cart/" + cartId + "/product/quantity", token, Map.of(
                "product_id", variantProduct.getId(),
                "product_variant_id", variant1.getId(),
                "quantity", 4
        ));
        assertThat(updateVariant.getStatusCode()).isEqualTo(HttpStatus.OK);

        Integer variantUpdated = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid and quantity = 4",
                Integer.class, cartId, variantProduct.getId().toString(), variant1.getId().toString());
        Integer variantUntouched = jdbc.queryForObject("select count(*) from cart_product where cart_id = ?::uuid and product_id = ?::uuid and product_variant_id = ?::uuid and quantity = 1",
                Integer.class, cartId, variantProduct.getId().toString(), variant2.getId().toString());
        assertThat(variantUpdated).isEqualTo(1);
        assertThat(variantUntouched).isEqualTo(1);
    }

    @Test
    void it_returns_the_full_cart() {
        String email = "qty-shape-" + UUID.randomUUID() + "@example.com";
        String token = registerAndLogin(email);
        var product = createProduct(10);
        var add = exchange(HttpMethod.POST, "/api/cart/addItem", token, Map.of("product_id", product.getId(), "quantity", 1));
        String cartId = String.valueOf(firstCart(add).get("id"));

        var res = exchange(HttpMethod.PATCH, "/api/cart/" + cartId + "/product/quantity", token, Map.of(
                "product_id", product.getId(),
                "quantity", 4
        ));
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> carts = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(carts).hasSize(1);
        var cart = carts.get(0);
        assertThat(String.valueOf(cart.get("id"))).isEqualTo(cartId);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> products = (List<Map<String, Object>>) cart.get("products");
        assertThat(String.valueOf(products.get(0).get("product_id"))).isEqualTo(product.getId().toString());
        assertThat(((Number) products.get(0).get("quantity")).longValue()).isEqualTo(4L);
    }
}
