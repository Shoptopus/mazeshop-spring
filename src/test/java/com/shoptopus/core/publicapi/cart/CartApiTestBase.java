package com.shoptopus.core.publicapi.cart;

import com.shoptopus.core.IntegrationTestBase;
import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

abstract class CartApiTestBase extends IntegrationTestBase {

    @Autowired
    protected TestRestTemplate restTemplate;

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected ProductRepository productRepository;

    @Autowired
    protected ProductVariantRepository productVariantRepository;

    protected String registerAndLogin(String email) {
        var register = restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Cart",
                "last_name", "Tester",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assertThat(register.getStatusCode().is2xxSuccessful()).isTrue();

        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        assertThat(login.getStatusCode().is2xxSuccessful()).isTrue();
        return (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
    }

    protected UUID userIdByEmail(String email) {
        return UUID.fromString(jdbc.queryForObject(
                "select id::text from users where lower(email) = lower(?) and deleted_at is null",
                String.class,
                email
        ));
    }

    protected ResponseEntity<Map> exchange(HttpMethod method, String path, String token, Object body) {
        HttpHeaders headers = new HttpHeaders();
        if (token != null) {
            headers.setBearerAuth(token);
        }
        return restTemplate.exchange(path, method, new HttpEntity<>(body, headers), Map.class);
    }

    protected ResponseEntity<Map> delete(String path, String token) {
        return exchange(HttpMethod.DELETE, path, token, null);
    }

    protected Product createProduct(long stock) {
        Product p = new Product();
        p.setName(Map.of("en", "Product " + UUID.randomUUID()));
        p.setSlug("product-" + UUID.randomUUID());
        p.setShortDescription(Map.of("en", "Short"));
        p.setDescription(Map.of("en", "Desc"));
        p.setPrice(BigDecimal.valueOf(10));
        p.setStock(stock);
        p.setVirtual(false);
        return productRepository.save(p);
    }

    protected ProductVariant createVariant(Product product, long stock) {
        ProductVariant variant = new ProductVariant();
        variant.setProduct(product);
        variant.setSlug("variant-" + UUID.randomUUID());
        variant.setPrice(product.getPrice());
        variant.setStock(stock);
        variant.setDescription(Map.of("en", "Variant"));
        variant.setAttributeOptions(Map.of("size", "M"));
        return productVariantRepository.save(variant);
    }

    @SuppressWarnings("unchecked")
    protected Map<String, Object> firstCart(ResponseEntity<Map> response) {
        List<Map<String, Object>> carts = (List<Map<String, Object>>) response.getBody().get("data");
        assertThat(carts).isNotEmpty();
        return carts.get(0);
    }
}
