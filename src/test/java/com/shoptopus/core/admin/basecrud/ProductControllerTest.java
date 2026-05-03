package com.shoptopus.core.admin.basecrud;

import com.shoptopus.core.catalog.Product;
import com.shoptopus.core.catalog.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductControllerTest extends AdminControllerTestBase {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void test_can_create_update_show_and_delete_product() {
        String token = token();
        String categoryId = seedCategory();
        String tagId = seedTag();

        var create = authed(token, HttpMethod.POST, "/api/admin/product", Map.of(
                "name", Map.of("en", "Test Product"),
                "slug", "product-" + UUID.randomUUID(),
                "short_description", Map.of("en", "Short"),
                "description", Map.of("en", "Long"),
                "price", 9.99,
                "stock", 5,
                "category_ids", java.util.List.of(categoryId),
                "tag_ids", java.util.List.of(tagId)
        ));
        assert2xx(create);
        UUID productId = UUID.fromString(((Map<?, ?>) create.getBody().get("data")).get("id").toString());
        Product product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName().get("en")).isEqualTo("Test Product");
        assertThat(product.getCategories()).hasSize(1);
        assertThat(product.getTags()).hasSize(1);

        var list = authed(token, HttpMethod.GET, "/api/admin/products", null);
        assert2xx(list);
        Map<?, ?> listed = ((java.util.List<Map<?, ?>>) list.getBody().get("data")).stream()
                .filter(item -> productId.toString().equals(String.valueOf(item.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(listed.get("name")).isEqualTo(Map.of("en", "Test Product"));

        var summary = authed(token, HttpMethod.GET, "/api/admin/products/summary", null);
        assert2xx(summary);
        assertThat(((Map<?, ?>) summary.getBody().get("data")).get("summary")).isInstanceOf(Map.class);

        var update = authed(token, HttpMethod.POST, "/api/admin/product/" + productId, Map.of(
                "name", Map.of("en", "Updated Product"),
                "stock", 13,
                "status", 1
        ));
        assert2xx(update);
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getName().get("en")).isEqualTo("Updated Product");
        assertThat(product.getStock()).isEqualTo(13L);
        assertThat(product.getStatus()).isEqualTo(1);

        var show = authed(token, HttpMethod.GET, "/api/admin/product/" + productId, null);
        assert2xx(show);
        Map<?, ?> showData = (Map<?, ?>) show.getBody().get("data");
        assertThat(showData.get("id")).isEqualTo(productId.toString());
        assertThat(showData.get("categories")).isInstanceOf(java.util.List.class);
        assertThat(showData.get("tags")).isInstanceOf(java.util.List.class);

        assert2xx(authed(token, HttpMethod.DELETE, "/api/admin/product/" + productId, null));
        product = productRepository.findById(productId).orElseThrow();
        assertThat(product.getDeletedAt()).isNotNull();
    }

    @Test
    void test_product_creation_validation() {
        String token = token();
        var response = authed(token, HttpMethod.POST, "/api/admin/product", Map.of("price", 12.50));
        assertValidationFailed(response, "name field is required");
    }

    @Test
    void products_require_permissions() {
        String email = "products-manager-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Products",
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
        String token = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");

        assertThat(authed(token, HttpMethod.GET, "/api/admin/products", null).getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(authed(token, HttpMethod.POST, "/api/admin/product", Map.of(
                "name", Map.of("en", "Allowed product"),
                "short_description", Map.of("en", "Short"),
                "description", Map.of("en", "Long"),
                "price", 10,
                "stock", 1
        )).getStatusCode().is2xxSuccessful()).isTrue();
    }

    private String seedCategory() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into product_categories(id, name, slug, description, enabled, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())
                """,
                id, "{\"en\":\"Product category\"}", "product-category-" + id, "{\"en\":\"Product category description\"}");
        return id;
    }

    private String seedTag() {
        String id = UUID.randomUUID().toString();
        jdbc.update("""
                insert into product_tags(id, name, slug, description, enabled, display_badge, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, true, false, now(), now())
                """,
                id, "{\"en\":\"Product tag\"}", "product-tag-" + id, "{\"en\":\"Product tag description\"}");
        return id;
    }
}
