package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.ProductVariant;
import com.shoptopus.core.catalog.repository.ProductRepository;
import com.shoptopus.core.catalog.repository.ProductVariantRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductVariantControllerTest extends AdminControllerTestBase {

    @Autowired
    private ProductRepository productRepository;
    @Autowired
    private ProductVariantRepository variantRepository;

    @Test
    void test_variant_can_be_created_updated_and_deleted() {
        String token = token();
        Product product = new Product();
        product.setName(Map.of("en", "Parent Product"));
        product.setSlug("parent-" + UUID.randomUUID());
        product.setShortDescription(Map.of("en", "Short"));
        product.setDescription(Map.of("en", "Long"));
        product.setPrice(BigDecimal.TEN);
        product.setStock(10L);
        productRepository.save(product);

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/product/" + product.getId() + "/variant", Map.of(
                "name", Map.of("en", "Large"),
                "price", 14.95,
                "stock", 8,
                "attribute_options", Map.of("size", "large")
        )));
        ProductVariant variant = variantRepository.findAll().stream().filter(v -> v.getProduct().getId().equals(product.getId())).findFirst().orElseThrow();
        assertThat(variant.getStock()).isEqualTo(8L);
        UUID variantId = variant.getId();

        var list = authed(token, HttpMethod.GET, "/api/admin/product/" + product.getId() + "/variants", null);
        assert2xx(list);
        Map<?, ?> row = ((List<Map<?, ?>>) ((Map<?, ?>) list.getBody().get("data")).get("data")).stream()
                .filter(item -> variantId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.get("stock")).isEqualTo(8);

        var show = authed(token, HttpMethod.GET, "/api/admin/product/" + product.getId() + "/variant/" + variantId, null);
        assert2xx(show);
        Map<?, ?> data = (Map<?, ?>) show.getBody().get("data");
        assertThat(data.get("id")).isEqualTo(variantId.toString());
        assertThat(data.get("description")).isEqualTo(Map.of("en", "Large"));
        assertThat(data.get("description_display")).isEqualTo("Large");

        assert2xx(authed(token, HttpMethod.POST, "/api/admin/product/" + product.getId() + "/variant/" + variantId, Map.of("stock", 21)));
        variant = variantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getStock()).isEqualTo(21L);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/product/" + product.getId() + "/variant/" + variantId, null));
        variant = variantRepository.findById(variantId).orElseThrow();
        assertThat(variant.getDeletedAt()).isNotNull();
    }

    @Test
    void variant_permissions_and_validation_are_enforced() {
        String token = token();
        Product product = new Product();
        product.setName(Map.of("en", "Parent Product"));
        product.setSlug("parent-" + UUID.randomUUID());
        product.setShortDescription(Map.of("en", "Short"));
        product.setDescription(Map.of("en", "Long"));
        product.setPrice(BigDecimal.TEN);
        product.setStock(10L);
        productRepository.save(product);

        var invalidPrice = authed(token, HttpMethod.POST, "/api/admin/product/" + product.getId() + "/variant", Map.of(
                "name", Map.of("en", "Broken"),
                "price", "oops"
        ));
        assertValidationFailed(invalidPrice, "price field is invalid");

        String email = "variant-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Variant",
                "last_name", "Manager",
                "email", email,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(email, "catalog_manager");
        assignPermissionToRole("catalog_manager", "products.view");
        assignPermissionToRole("catalog_manager", "products.manage");
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", email,
                "password", "password123"
        ), Map.class);
        String permittedToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(permittedToken, HttpMethod.GET, "/api/admin/product/" + product.getId() + "/variants", null).getStatusCode().is2xxSuccessful()).isTrue();

        String blockedEmail = "variant-blocked-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Variant",
                "last_name", "Blocked",
                "email", blockedEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        assignRole(blockedEmail, "customer");
        var blockedLogin = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", blockedEmail,
                "password", "password123"
        ), Map.class);
        String blockedToken = (String) ((Map<?, ?>) ((Map<?, ?>) blockedLogin.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(blockedToken, HttpMethod.GET, "/api/admin/product/" + product.getId() + "/variants", null).getStatusCode()).isEqualTo(org.springframework.http.HttpStatus.FORBIDDEN);
    }
}
