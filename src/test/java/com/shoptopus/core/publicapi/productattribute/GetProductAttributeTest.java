package com.shoptopus.core.publicapi.productattribute;

import com.shoptopus.core.publicapi.catalog.CatalogPublicApiTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class GetProductAttributeTest extends CatalogPublicApiTestBase {

    @Test
    void it_can_return_a_product_attribute_by_its_id() {
        UUID id = createAttribute("Length", true);
        createAttributeOption(id, "Short", true);

        var res = exchange(HttpMethod.GET, "/api/product_attribute/" + id, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(String.valueOf(data.get("id"))).isEqualTo(id.toString());
    }

    @Test
    void it_returns_all_required_fields() {
        UUID id = createAttribute("Width", true);
        createAttributeOption(id, "Narrow", true);

        var res = exchange(HttpMethod.GET, "/api/product_attribute/" + id, null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.OK);
        var data = (Map<String, Object>) res.getBody().get("data");
        assertThat(data.keySet()).contains("id", "name", "slug", "type", "image", "options");
    }

    @Test
    void it_returns_only_enabled_non_deleted_associated_options() {
        UUID id = createAttribute("Height", true);
        UUID enabled = createAttributeOption(id, "Tall", true);
        UUID disabled = createAttributeOption(id, "Low", false);
        UUID deleted = createAttributeOption(id, "Mid", true);
        jdbc.update("update product_attribute_options set deleted_at = now() where id = ?::uuid", deleted.toString());

        var res = exchange(HttpMethod.GET, "/api/product_attribute/" + id, null, null);
        var data = (Map<String, Object>) res.getBody().get("data");
        var optionIds = ((List<Map<String, Object>>) data.get("options")).stream()
                .map(o -> String.valueOf(o.get("id")))
                .toList();
        assertThat(optionIds).contains(enabled.toString());
        assertThat(optionIds).doesNotContain(disabled.toString(), deleted.toString());
    }

    @Test
    void non_existing_attribute_returns_not_found() {
        var res = exchange(HttpMethod.GET, "/api/product_attribute/" + UUID.randomUUID(), null, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
