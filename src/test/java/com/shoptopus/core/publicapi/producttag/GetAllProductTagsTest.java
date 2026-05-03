package com.shoptopus.core.publicapi.producttag;

import com.shoptopus.core.publicapi.catalog.CatalogPublicApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetAllProductTagsTest extends CatalogPublicApiTestBase {

    @Test
    void it_returns_the_correct_format() {
        var res = exchange(HttpMethod.GET, "/api/product_tags", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(res.getBody()).containsKeys("message", "data", "records", "total_records");
    }

    @Test
    void it_returns_all_required_fields() {
        UUID tagId = createTag("Featured", true);

        var res = exchange(HttpMethod.GET, "/api/product_tags", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);

        var row = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .filter(r -> tagId.toString().equals(String.valueOf(r.get("id"))))
                .findFirst()
                .orElseThrow();
        assertThat(row.keySet()).contains("id", "name", "slug", "description");
    }

    @Test
    void soft_deleted_and_disabled_product_tags_are_not_returned() {
        UUID deletedTagId = createTag("Deleted Tag", true);
        jdbc.update("update product_tags set deleted_at = now() where id = ?::uuid", deletedTagId.toString());
        UUID disabledTagId = createTag("Disabled Tag", false);

        var res = exchange(HttpMethod.GET, "/api/product_tags", null, null);
        var ids = ((List<Map<String, Object>>) res.getBody().get("data")).stream()
                .map(r -> String.valueOf(r.get("id")))
                .toList();
        assertThat(ids).doesNotContain(deletedTagId.toString(), disabledTagId.toString());
    }

    @Test
    void it_returns_the_count() {
        createTag("Count Tag A", true);
        createTag("Count Tag B", true);
        var res = exchange(HttpMethod.GET, "/api/product_tags", null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) res.getBody().get("total_records")).intValue()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void tag_endpoint_can_be_retrieved_by_id() {
        UUID tagId = createTag("Tag by id", true);
        var res = exchange(HttpMethod.GET, "/api/product_tag/" + tagId, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("id"))).isEqualTo(tagId.toString());
    }
}
