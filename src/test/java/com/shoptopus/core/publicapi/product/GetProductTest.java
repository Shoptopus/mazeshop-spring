package com.shoptopus.core.publicapi.product;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetProductTest extends ProductApiTestBase {

    @Test
    void it_can_return_a_product_by_id() {
        var productId = createProduct("Find By Id");
        var res = exchange(HttpMethod.GET, "/api/product/" + productId, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("id"))).isEqualTo(productId.toString());
    }

    @Test
    void it_can_return_a_product_by_slug() {
        var productId = createProduct("Find By Slug");
        String slug = jdbc.queryForObject("select slug from products where id = ?::uuid", String.class, productId.toString());

        var res = exchange(HttpMethod.GET, "/api/product/slug/" + slug, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("id"))).isEqualTo(productId.toString());
        assertThat(String.valueOf(data.get("slug"))).isEqualTo(slug);
    }

    @Test
    void it_returns_all_required_fields_with_final_price() {
        var productId = createProduct("Required Detail Fields");

        var res = exchange(HttpMethod.GET, "/api/product/" + productId, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");

        assertThat(data.keySet()).contains(
                "id",
                "name",
                "slug",
                "shortDescription",
                "description",
                "price",
                "finalPrice",
                "stock",
                "sku",
                "categoryIds",
                "tagIds"
        );
    }

    @Test
    void it_returns_related_category_and_tag_ids() {
        var productId = createProduct("Detail Relations");
        var categoryId = createCategory("Detail Category");
        var tagId = createTag("Detail Tag");
        attachCategory(productId, categoryId);
        attachTag(productId, tagId);

        var res = exchange(HttpMethod.GET, "/api/product/" + productId, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");

        var categoryIds = ((java.util.List<?>) data.get("categoryIds")).stream().map(String::valueOf).toList();
        var tagIds = ((java.util.List<?>) data.get("tagIds")).stream().map(String::valueOf).toList();
        assertThat(categoryIds).contains(categoryId.toString());
        assertThat(tagIds).contains(tagId.toString());
    }

    @Test
    void it_returns_not_found_for_soft_deleted_product() {
        var productId = createProduct("Soft Deleted Detail");
        jdbc.update("update products set deleted_at = now() where id = ?::uuid", productId.toString());

        var res = exchange(HttpMethod.GET, "/api/product/" + productId, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private UUID createProduct(String name) {
        UUID id = UUID.randomUUID();
        String slug = name.toLowerCase().replace(" ", "-") + "-" + id;
        jdbc.update(
                "insert into products(id, name, slug, short_description, description, price, status, purchase_count, stock, virtual, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?::jsonb, ?::jsonb, ?, 0, 0, 100, false, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                slug,
                "{\"en\":\"Short\"}",
                "{\"en\":\"Description\"}",
                34.99
        );
        return id;
    }

    private UUID createCategory(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into product_categories(id, parent_id, name, slug, description, enabled, created_at, updated_at) " +
                        "values (?::uuid, null, ?::jsonb, ?, ?::jsonb, true, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                name.toLowerCase().replace(" ", "-") + "-" + id,
                "{\"en\":\"Description\"}"
        );
        return id;
    }

    private UUID createTag(String name) {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into product_tags(id, name, slug, description, display_badge, enabled, created_at, updated_at) " +
                        "values (?::uuid, ?::jsonb, ?, ?::jsonb, false, true, now(), now())",
                id.toString(),
                "{\"en\":\"" + name + "\"}",
                name.toLowerCase().replace(" ", "-") + "-" + id,
                "{\"en\":\"Description\"}"
        );
        return id;
    }

    private void attachCategory(UUID productId, UUID categoryId) {
        jdbc.update(
                "insert into product_product_category(product_id, product_category_id) values (?::uuid, ?::uuid)",
                productId.toString(),
                categoryId.toString()
        );
    }

    private void attachTag(UUID productId, UUID tagId) {
        jdbc.update(
                "insert into product_product_tag(product_id, product_tag_id) values (?::uuid, ?::uuid)",
                productId.toString(),
                tagId.toString()
        );
    }
}
