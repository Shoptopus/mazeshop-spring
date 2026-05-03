package com.shoptopus.core.publicapi.productcategory;

import com.shoptopus.core.publicapi.catalog.CatalogPublicApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllProductCategoriesTest extends CatalogPublicApiTestBase {

    @Test
    void it_returns_the_correct_format() {
        var res = exchange(HttpMethod.GET, "/api/product_categories", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKeys("message", "data", "records", "total_records");
    }

    @Test
    void it_returns_all_required_fields() {
        UUID id = createCategory("Food", true);
        var res = exchange(HttpMethod.GET, "/api/product_categories", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var row = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .filter(r -> id.toString().equals(String.valueOf(r.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.keySet()).contains("id", "parentId", "name", "slug", "description");
    }

    @Test
    void soft_deleted_and_disabled_product_categories_are_not_returned() {
        UUID deletedId = createCategory("Deleted Cat", true);
        jdbc.update("update product_categories set deleted_at = now() where id = ?::uuid", deletedId.toString());
        UUID disabledId = createCategory("Disabled Cat", false);

        var res = exchange(HttpMethod.GET, "/api/product_categories", null, null);
        var ids = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .map(r -> String.valueOf(r.get("id")))
                .toList();
        assertThat(ids).doesNotContain(deletedId.toString(), disabledId.toString());
    }

    @Test
    void it_returns_the_count() {
        createCategory("Count A", true);
        createCategory("Count B", true);

        var res = exchange(HttpMethod.GET, "/api/product_categories", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) res.getBody().get("total_records")).intValue()).isGreaterThanOrEqualTo(2);
    }
}
