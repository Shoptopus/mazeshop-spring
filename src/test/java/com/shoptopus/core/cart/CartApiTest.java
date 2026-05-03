package com.shoptopus.core.cart;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CartApiTest extends IntegrationTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void add_item_creates_cart_and_returns_shape() {
        var p = new Product();
        p.setName(Map.of("en", "Maze Lamp"));
        p.setSlug("maze-lamp-2");
        p.setShortDescription(Map.of("en", "Short"));
        p.setDescription(Map.of("en", "Desc"));
        p.setPrice(BigDecimal.valueOf(10));
        p.setStock(10L);
        p.setVirtual(false);
        productRepository.save(p);

        var req = Map.of("productId", p.getId(), "quantity", 2);
        var res = restTemplate.postForEntity("/api/cart/addItem", req, Map.class);

        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();
        var data = (java.util.List<?>) res.getBody().get("data");
        assertThat(data).isNotEmpty();
    }

    @Test
    void add_item_validation_and_stock_errors_match_expected_behavior() {
        var missing = restTemplate.postForEntity("/api/cart/addItem", Map.of(), Map.class);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(missing.getBody().get("errors")).asString().contains("productId", "quantity");

        var invalidProduct = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "productId", UUID.randomUUID(),
                "quantity", 1
        ), Map.class);
        assertThat(invalidProduct.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(invalidProduct.getBody().get("developer_message")).isEqualTo("Invalid product id");

        var outOfStock = new Product();
        outOfStock.setName(Map.of("en", "Out"));
        outOfStock.setSlug("out-" + UUID.randomUUID());
        outOfStock.setShortDescription(Map.of("en", "Short"));
        outOfStock.setDescription(Map.of("en", "Desc"));
        outOfStock.setPrice(BigDecimal.valueOf(10));
        outOfStock.setStock(0L);
        outOfStock.setVirtual(false);
        productRepository.save(outOfStock);

        var out = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "productId", outOfStock.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(out.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(out.getBody().get("developer_message")).isEqualTo("Out of stock.");

        var lowStock = new Product();
        lowStock.setName(Map.of("en", "Low"));
        lowStock.setSlug("low-" + UUID.randomUUID());
        lowStock.setShortDescription(Map.of("en", "Short"));
        lowStock.setDescription(Map.of("en", "Desc"));
        lowStock.setPrice(BigDecimal.valueOf(10));
        lowStock.setStock(1L);
        lowStock.setVirtual(false);
        productRepository.save(lowStock);

        var unavailable = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "productId", lowStock.getId(),
                "quantity", 2
        ), Map.class);
        assertThat(unavailable.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(unavailable.getBody().get("developer_message")).isEqualTo("Only 1 left.");
    }

    @Test
    void adding_same_item_twice_increments_quantity() {
        var p = new Product();
        p.setName(Map.of("en", "Maze Lamp"));
        p.setSlug("maze-lamp-3");
        p.setShortDescription(Map.of("en", "Short"));
        p.setDescription(Map.of("en", "Desc"));
        p.setPrice(BigDecimal.valueOf(10));
        p.setStock(10L);
        p.setVirtual(false);
        productRepository.save(p);

        var first = restTemplate.postForEntity("/api/cart/addItem", Map.of("productId", p.getId(), "quantity", 1), Map.class);
        var firstData = (java.util.List<?>) first.getBody().get("data");
        var firstCart = (Map<?, ?>) firstData.get(0);
        String cartId = firstCart.get("id").toString();

        var second = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "cartId", cartId,
                "productId", p.getId(),
                "quantity", 1
        ), Map.class);
        assertThat(second.getStatusCode().is2xxSuccessful()).isTrue();
        var secondData = (java.util.List<?>) second.getBody().get("data");
        var secondCart = (Map<?, ?>) secondData.get(0);
        var products = (java.util.List<?>) secondCart.get("products");
        var product = (Map<?, ?>) products.get(0);
        assertThat(((Number) product.get("quantity")).longValue()).isEqualTo(2L);
    }

    @Test
    void update_cart_requires_authentication() {
        var p = new Product();
        p.setName(Map.of("en", "Desk"));
        p.setSlug("desk-" + UUID.randomUUID());
        p.setShortDescription(Map.of("en", "Short"));
        p.setDescription(Map.of("en", "Desc"));
        p.setPrice(BigDecimal.valueOf(10));
        p.setStock(10L);
        p.setVirtual(false);
        productRepository.save(p);

        var add = restTemplate.postForEntity("/api/cart/addItem", Map.of("productId", p.getId(), "quantity", 1), Map.class);
        var data = (java.util.List<?>) add.getBody().get("data");
        String cartId = ((Map<?, ?>) data.get(0)).get("id").toString();

        var res = restTemplate.exchange("/api/cart/" + cartId, HttpMethod.PATCH, new HttpEntity<>(Map.of(), new HttpHeaders()), Map.class);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(res.getBody().get("user_message")).isEqualTo("Unauthenticated");
    }

    @Test
    void add_item_accepts_snake_case_and_returns_snake_case_keys() {
        var p = new Product();
        p.setName(Map.of("en", "Snake Cart"));
        p.setSlug("snake-cart-" + UUID.randomUUID());
        p.setShortDescription(Map.of("en", "Short"));
        p.setDescription(Map.of("en", "Desc"));
        p.setPrice(BigDecimal.valueOf(10));
        p.setStock(10L);
        p.setVirtual(false);
        productRepository.save(p);

        var res = restTemplate.postForEntity("/api/cart/addItem", Map.of(
                "product_id", p.getId(),
                "quantity", 2
        ), Map.class);
        assertThat(res.getStatusCode().is2xxSuccessful()).isTrue();

        var data = (java.util.List<?>) res.getBody().get("data");
        var cart = (Map<?, ?>) data.get(0);
        assertThat(cart.containsKey("user_id")).isTrue();
        assertThat(cart.containsKey("ip_address")).isTrue();

        var products = (java.util.List<?>) cart.get("products");
        var item = (Map<?, ?>) products.get(0);
        assertThat(item.containsKey("product_id")).isTrue();
        assertThat(item.containsKey("product_variant_id")).isTrue();
        assertThat(item.containsKey("item_original_price")).isTrue();
        assertThat(item.containsKey("item_final_price")).isTrue();
        assertThat(item.containsKey("subtotal_original_price")).isTrue();
        assertThat(item.containsKey("subtotal_final_price")).isTrue();
        assertThat(item.containsKey("remaining_stock")).isTrue();
        assertThat(item.containsKey("in_other_carts")).isTrue();
    }
}
