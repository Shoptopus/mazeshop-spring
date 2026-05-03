package com.shoptopus.core.admin.basecrud;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AdminUiProductFavoritesTest extends AdminControllerTestBase {

    @Test
    void product_and_customer_detail_include_favorite_relationships() {
        UUID productId = UUID.randomUUID();
        String slug = "favorite-product-" + UUID.randomUUID();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, status, stock, virtual, favourites, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, 10.00, 1, 5, false, 0, now(), now())
                """,
                productId.toString(),
                "{\"en\":\"Favorite Product\"}",
                slug,
                "{\"en\":\"Short\"}",
                "{\"en\":\"Description\"}");

        String customerEmail = "favorite-customer-" + UUID.randomUUID() + "@example.com";
        restTemplate.postForEntity("/api/auth/register", Map.of(
                "first_name", "Favorite",
                "last_name", "Customer",
                "email", customerEmail,
                "password", "password123",
                "password_confirmation", "password123"
        ), Map.class);
        var login = restTemplate.postForEntity("/api/auth/login", Map.of(
                "email", customerEmail,
                "password", "password123"
        ), Map.class);
        String customerToken = (String) ((Map<?, ?>) ((Map<?, ?>) login.getBody().get("data")).get("auth")).get("token");
        String customerId = jdbc.queryForObject("select id::text from users where lower(email)=lower(?) limit 1", String.class, customerEmail);

        var toggle = authed(customerToken, HttpMethod.POST, "/api/product/" + productId + "/favorite", Map.of());
        assertThat(toggle.getStatusCode()).isEqualTo(HttpStatus.OK);

        String adminToken = token();

        var product = authed(adminToken, HttpMethod.GET, "/api/admin-ui/products/" + productId, null);
        assert2xx(product);
        var productData = (Map<?, ?>) product.getBody().get("data");
        assertThat(productData.get("favourites")).isEqualTo(1);
        assertThat(((Map<?, ?>) productData.get("metrics")).get("favourites")).isEqualTo(1);
        assertThat((java.util.List<?>) productData.get("favorited_by")).hasSize(1);

        var customer = authed(adminToken, HttpMethod.GET, "/api/admin-ui/customers/" + customerId, null);
        assert2xx(customer);
        var customerData = (Map<?, ?>) customer.getBody().get("data");
        assertThat(((Map<?, ?>) customerData.get("metrics")).get("favorite_products")).isEqualTo(1);
        assertThat((java.util.List<?>) customerData.get("favorite_products")).hasSize(1);
    }

    @Test
    void product_detail_includes_category_parent_metadata_without_lazy_init_failure() {
        UUID parentCategoryId = UUID.randomUUID();
        UUID childCategoryId = UUID.randomUUID();
        jdbc.update("""
                insert into product_categories(id, name, slug, description, enabled, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())
                """,
                parentCategoryId.toString(),
                "{\"en\":\"Home & Living\"}",
                "home-living",
                "{\"en\":\"\"}");
        jdbc.update("""
                insert into product_categories(id, parent_id, name, slug, description, enabled, created_at, updated_at)
                values (?::uuid, ?::uuid, ?::jsonb, ?, ?::jsonb, true, now(), now())
                """,
                childCategoryId.toString(),
                parentCategoryId.toString(),
                "{\"en\":\"Coasters\"}",
                "coasters",
                "{\"en\":\"\"}");

        UUID productId = UUID.randomUUID();
        String slug = "category-detail-product-" + UUID.randomUUID();
        jdbc.update("""
                insert into products(id, name, slug, short_description, description, price, status, stock, virtual, favourites, created_at, updated_at)
                values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, 10.00, 0, 5, false, 0, now(), now())
                """,
                productId.toString(),
                "{\"en\":\"Category Product\"}",
                slug,
                "{\"en\":\"Short\"}",
                "{\"en\":\"Description\"}");
        jdbc.update("insert into product_product_category(product_id, product_category_id) values (?::uuid, ?::uuid)",
                productId.toString(),
                childCategoryId.toString());

        var product = authed(token(), HttpMethod.GET, "/api/admin-ui/products/" + productId, null);
        assert2xx(product);
        var productData = (Map<?, ?>) product.getBody().get("data");
        var categories = (java.util.List<?>) productData.get("categories");
        assertThat(categories).hasSize(1);
        var category = (Map<?, ?>) categories.get(0);
        assertThat(category.get("name")).isEqualTo("Coasters");
        assertThat(category.get("slug")).isEqualTo("coasters");
        assertThat(category.get("parent")).isEqualTo(Map.of(
                "id", parentCategoryId.toString(),
                "name", "Home & Living",
                "slug", "home-living"
        ));
    }
}
