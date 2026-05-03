package com.shoptopus.core.publicapi.productcategory;

import com.shoptopus.core.publicapi.catalog.CatalogPublicApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetProductCategoryTest extends CatalogPublicApiTestBase {

    @Test
    void it_can_return_a_product_category_by_its_id() {
        UUID id = createCategory("By Id Category", true);
        var res = exchange(HttpMethod.GET, "/api/product_category/" + id, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("id"))).isEqualTo(id.toString());
    }

    @Test
    void it_can_return_a_product_category_by_its_slug() {
        UUID id = createCategory("By Slug Category", true);
        String slug = jdbc.queryForObject("select slug from product_categories where id = ?::uuid", String.class, id.toString());

        var res = exchange(HttpMethod.GET, "/api/product_category/slug/" + slug, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("id"))).isEqualTo(id.toString());
        assertThat(String.valueOf(data.get("slug"))).isEqualTo(slug);
    }

    @Test
    void it_returns_all_required_fields() {
        UUID id = createCategory("Required Fields Category", true);

        var res = exchange(HttpMethod.GET, "/api/product_category/" + id, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.keySet()).contains("id", "parentId", "name", "slug", "description");
    }

    @Test
    void soft_deleted_category_returns_not_found() {
        UUID id = createCategory("Deleted Detail Category", true);
        jdbc.update("update product_categories set deleted_at = now() where id = ?::uuid", id.toString());

        var res = exchange(HttpMethod.GET, "/api/product_category/" + id, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
