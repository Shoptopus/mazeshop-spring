package com.shoptopus.core.publicapi.product;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllProductsTest extends ProductApiTestBase {

    @Test
    void it_returns_the_correct_format_with_records_and_total_records() {
        ensureProduct();

        var res = exchange(HttpMethod.GET, "/api/products", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKeys("message", "data", "records", "total_records");
        assertThat(res.getBody().get("data")).isInstanceOf(List.class);
    }

    @Test
    void it_returns_all_required_fields_and_final_price() {
        var productId = createProduct("List Required Fields");

        var res = exchange(HttpMethod.GET, "/api/products?search=Required%20Fields", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var rows = (List<Map<String, Object>>) res.getBody().get("data");
        assertThat(rows).isNotEmpty();

        var product = rows.stream()
                .filter(r -> productId.toString().equals(String.valueOf(r.get("id"))))
                .findFirst()
                .orElseThrow();

        assertThat(product.keySet()).contains(
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
    void soft_deleted_products_are_not_returned() {
        var visibleId = createProduct("Visible Product");
        var deletedId = createProduct("Deleted Product");
        jdbc.update("update products set deleted_at = now() where id = ?::uuid", deletedId.toString());

        var res = exchange(HttpMethod.GET, "/api/products?search=Product", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var ids = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .map(row -> String.valueOf(row.get("id")))
                .toList();
        assertThat(ids).contains(visibleId.toString());
        assertThat(ids).doesNotContain(deletedId.toString());
    }

    @Test
    void products_can_be_filtered_by_category_id() {
        var categoryA = createCategory("Category A");
        var categoryB = createCategory("Category B");
        var productA = createProduct("Category Product A");
        var productB = createProduct("Category Product B");

        attachCategory(productA, categoryA);
        attachCategory(productB, categoryB);

        var res = exchange(HttpMethod.GET, "/api/products?categoryId=" + categoryA, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var ids = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .map(row -> String.valueOf(row.get("id")))
                .toList();
        assertThat(ids).contains(productA.toString());
        assertThat(ids).doesNotContain(productB.toString());
    }

    @Test
    void products_return_related_category_and_tag_ids() {
        var categoryId = createCategory("Attached Category");
        var tagId = createTag("Attached Tag");
        var productId = createProduct("Attached Relations");
        attachCategory(productId, categoryId);
        attachTag(productId, tagId);

        var res = exchange(HttpMethod.GET, "/api/products?search=Attached%20Relations", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var row = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .filter(p -> productId.toString().equals(String.valueOf(p.get("id"))))
                .findFirst()
                .orElseThrow();

        var categoryIds = ((List<?>) row.get("categoryIds")).stream().map(String::valueOf).toList();
        var tagIds = ((List<?>) row.get("tagIds")).stream().map(String::valueOf).toList();
        assertThat(categoryIds).contains(categoryId.toString());
        assertThat(tagIds).contains(tagId.toString());
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
                24.99
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
